package com.dunctebot.sourcemanagers.tiktok;

import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.*;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.getHeaderValue;
import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.isSuccessWithContent;


/**
 * @see com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream PersistentHttpStream for the original implementation
 */
public class CopyOfPersistentHttpStream extends SeekableInputStream implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(CopyOfPersistentHttpStream.class);

    private static final long MAX_SKIP_DISTANCE = 512L * 1024L;

    private final HttpInterface httpInterface;
    protected final URI contentUrl;
    private int lastStatusCode;
    private CloseableHttpResponse currentResponse;
    private InputStream currentContent;
    protected long position;

    public CopyOfPersistentHttpStream(HttpInterface httpInterface, URI contentUrl, Long contentLength) {
        super(contentLength == null ? Units.CONTENT_LENGTH_UNKNOWN : contentLength, MAX_SKIP_DISTANCE);

        this.httpInterface = httpInterface;
        this.contentUrl = contentUrl;
        this.position = 0;
    }

    public int checkStatusCode() throws IOException {
        connect(true);

        return lastStatusCode;
    }

    public HttpResponse getCurrentResponse() {
        return currentResponse;
    }

    protected URI getConnectUrl() {
        return contentUrl;
    }

    protected boolean useHeadersForRange() {
        return true;
    }

    private static boolean validateStatusCode(HttpResponse response, boolean returnOnServerError) {
        int statusCode = response.getStatusLine().getStatusCode();

        System.out.println("STATUS CODE IS " + statusCode);

        // TIKTOK :D
        if (statusCode == 403) {
            return true;
        }

        if (returnOnServerError && statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            return false;
        } else if (!isSuccessWithContent(statusCode)) {
            throw new RuntimeException("Not success status code: " + statusCode);
        }
        return true;
    }

    private HttpGet getConnectRequest() {
        HttpGet request = new HttpGet(getConnectUrl());

        if (position > 0 && useHeadersForRange()) {
            request.setHeader(HttpHeaders.RANGE, "bytes=" + position + "-");
        }

        return request;
    }

    private void connect(boolean skipStatusCheck) throws IOException {
        if (currentResponse == null) {
            for (int i = 1; i >= 0; i--) {
                if (attemptConnect(skipStatusCheck, i > 0)) {
                    break;
                }
            }
        }
    }

    private boolean attemptConnect(boolean skipStatusCheck, boolean retryOnServerError) throws IOException {
        currentResponse = httpInterface.execute(getConnectRequest());
        lastStatusCode = currentResponse.getStatusLine().getStatusCode();

        if (!skipStatusCheck && !validateStatusCode(currentResponse, retryOnServerError)) {
            return false;
        }

        if (currentResponse.getEntity() == null) {
            currentContent = EmptyInputStream.INSTANCE;
            contentLength = 0;
            return true;
        }

        currentContent = new BufferedInputStream(currentResponse.getEntity().getContent());

        if (contentLength == Units.CONTENT_LENGTH_UNKNOWN) {
            Header header = currentResponse.getFirstHeader("Content-Length");

            if (header != null) {
                contentLength = Long.parseLong(header.getValue());
            }
        }

        return true;
    }

    private void handleNetworkException(IOException exception, boolean attemptReconnect) throws IOException {
        if (!attemptReconnect || !HttpClientTools.isRetriableNetworkException(exception)) {
            throw exception;
        }

        close();

        log.debug("Encountered retriable exception on url {}.", contentUrl, exception);
    }

    private int internalRead(boolean attemptReconnect) throws IOException {
        connect(false);

        try {
            int result = currentContent.read();
            if (result >= 0) {
                position++;
            }
            return result;
        } catch (IOException e) {
            handleNetworkException(e, attemptReconnect);
            return internalRead(false);
        }
    }

    @Override
    public int read() throws IOException {
        return internalRead(true);
    }

    private int internalRead(byte[] b, int off, int len, boolean attemptReconnect) throws IOException {
        connect(false);

        try {
            int result = currentContent.read(b, off, len);
            if (result >= 0) {
                position += result;
            }
            return result;
        } catch (IOException e) {
            handleNetworkException(e, attemptReconnect);
            return internalRead(b, off, len, false);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return internalRead(b, off, len, true);
    }

    private long internalSkip(long n, boolean attemptReconnect) throws IOException {
        connect(false);

        try {
            long result = currentContent.skip(n);
            if (result >= 0) {
                position += result;
            }
            return result;
        } catch (IOException e) {
            handleNetworkException(e, attemptReconnect);
            return internalSkip(n, false);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        return internalSkip(n, true);
    }

    private int internalAvailable(boolean attemptReconnect) throws IOException {
        connect(false);

        try {
            return currentContent.available();
        } catch (IOException e) {
            handleNetworkException(e, attemptReconnect);
            return internalAvailable(false);
        }
    }

    @Override
    public int available() throws IOException {
        return internalAvailable(true);
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (currentResponse != null) {
            try {
                currentResponse.close();
            } catch (IOException e) {
                log.debug("Failed to close response.", e);
            }

            currentResponse = null;
            currentContent = null;
        }
    }

    public void releaseConnection() {
        if (currentContent != null) {
            try {
                currentContent.close();
            } catch (IOException e) {
                log.debug("Failed to close response stream.", e);
            }
        }

        currentResponse = null;
        currentContent = null;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    protected void seekHard(long position) throws IOException {
        close();

        this.position = position;
    }

    @Override
    public boolean canSeekHard() {
        return contentLength != Units.CONTENT_LENGTH_UNKNOWN;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        if (currentResponse != null) {
            return Collections.singletonList(createIceCastHeaderProvider());
        } else {
            return Collections.emptyList();
        }
    }

    private AudioTrackInfoProvider createIceCastHeaderProvider() {
        AudioTrackInfoBuilder builder = AudioTrackInfoBuilder.empty()
            .setTitle(getHeaderValue(currentResponse, "icy-description"))
            .setAuthor(getHeaderValue(currentResponse, "icy-name"));

        if (builder.getTitle() == null) {
            builder.setTitle(getHeaderValue(currentResponse, "icy-url"));
        }

        return builder;
    }
}