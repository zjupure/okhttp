package okhttp3.internal.http;

import okhttp3.*;
import okhttp3.internal.connection.StreamAllocation;
import okhttp3.internal.framed.FramedConnection;
import okhttp3.internal.framed.FramedStream;
import okhttp3.internal.framed.Header;
import okio.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static okhttp3.internal.http.Http2xStream.readHttp2HeadersList;
import static okhttp3.internal.http.Http2xStream.readSpdy3HeadersList;

/**
 * A socket connection that can be used to send HTTP/1.1 messages.
 */
public class Http2cStream extends Http1xStream {

    private FramedConnection framedConnection;
    private FramedStream stream;

    public Http2cStream(OkHttpClient client, StreamAllocation streamAllocation, BufferedSource source,
                        BufferedSink sink){
        super(client, streamAllocation, source, sink);
    }

    @Override public void setFrameConnection(FramedConnection frameConnection) {
        this.framedConnection = frameConnection;
    }


    @Override public Response.Builder readResponseHeaders() throws IOException {

        if (framedConnection != null){
            // Upgrade to HTTP2
            List<Header> emptyHeaders = new ArrayList<>();
            stream = framedConnection.newStream(emptyHeaders, false, true);
            stream.readTimeout().timeout(client.readTimeoutMillis(), TimeUnit.MILLISECONDS);
            stream.writeTimeout().timeout(client.writeTimeoutMillis(), TimeUnit.MILLISECONDS);

            Response.Builder builder = framedConnection.isHttp2()
                    ? readHttp2HeadersList(stream.getResponseHeaders())
                    : readSpdy3HeadersList(stream.getResponseHeaders());
            builder.protocol(framedConnection.getProtocol());
            return builder;
        }

        return super.readResponseHeaders();
    }


    @Override public ResponseBody openResponseBody(Response response) throws IOException {

        if (framedConnection != null && stream != null){
            // Upgrade to HTTP/2
            Source source = new StreamFinishingSource(stream.getSource());
            return new RealResponseBody(response.headers(), Okio.buffer(source));
        }

        return super.openResponseBody(response);
    }


    class StreamFinishingSource extends ForwardingSource {
        public StreamFinishingSource(Source delegate) {
            super(delegate);
        }

        @Override public void close() throws IOException {
            if (streamAllocation != null) {
                streamAllocation.streamFinished(false, Http2cStream.this);
            }
            super.close();
        }
    }
}
