package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.util.Logging;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * Custom okhttp3 RequestBody implementation for progress.
 * Small modifications from hongyangAndroid's counting request listener example
 * From https://github.com/hongyangAndroid/okhttputils
 *   - /blob/master/okhttputils/src/main/java/com/zhy/http/okhttp/request/CountingRequestBody.java
 * (Apache License)
 */
public class CountingRequestBody extends RequestBody
{

    protected RequestBody delegate;
    protected UploadProgressListener listener;

    protected CountingSink countingSink;

    public CountingRequestBody(RequestBody delegate, UploadProgressListener listener)
    {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public MediaType contentType()
    {
        return delegate.contentType();
    }

    @Override
    public long contentLength() {
        try {
            return delegate.contentLength();
        } catch (IOException e) {
            Logging.error("Upload progress - content len error: ", e);
        }
        return -1;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        countingSink = new CountingSink(sink);
        BufferedSink bufferedSink = Okio.buffer(countingSink);

        delegate.writeTo(bufferedSink);

        bufferedSink.flush();
    }

    /**
     * custom ForwardingSink implementation to aggregate bytes written
     */
    protected final class CountingSink extends ForwardingSink {

        private long bytesWritten = 0;

        public CountingSink(Sink delegate)  {
            super(delegate);
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            super.write(source, byteCount);
            bytesWritten += byteCount;
            listener.onRequestProgress(bytesWritten, contentLength());
        }
    }

    public interface UploadProgressListener {
        void onRequestProgress(long bytesWritten, long contentLength);
    }

}