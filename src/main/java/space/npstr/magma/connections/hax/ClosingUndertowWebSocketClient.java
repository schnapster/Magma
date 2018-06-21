package space.npstr.magma.connections.hax;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.client.WebSocketClientNegotiation;
import io.undertow.websockets.core.WebSocketChannel;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.ClosingUndertowWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.UndertowWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.UndertowWebSocketSession;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.xnio.IoFuture;
import org.xnio.XnioWorker;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by napster on 22.04.18.
 * <p>
 * Plugs in our custom {@link ClosingUndertowWebSocketHandlerAdapter}, as well as uses a shared bufferpool to avoid
 * leaks. Leaks happen due to the {@link DefaultByteBufferPool} using a threadlocal variable that holds a reference to
 * an instance of an inner class of the pool preventing it from being GCed. Since pools can be shared, we opt for the
 * easy solution of using a single pool in Magma.
 *
 * Rest of the file is copypasta of the superclass(es) that is necessary to make that work.
 */
public class ClosingUndertowWebSocketClient extends UndertowWebSocketClient implements ClosingWebSocketClient {

    private static final int DEFAULT_POOL_BUFFER_SIZE = 16384; //recommended 16kb buffer.
    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    private final ByteBufferPool bufferPool;

    public ClosingUndertowWebSocketClient(final XnioWorker worker,
                                          final Consumer<WebSocketClient.ConnectionBuilder> builderConsumer) {
        this(worker, builderConsumer, DEFAULT_POOL_BUFFER_SIZE);
    }

    public ClosingUndertowWebSocketClient(final XnioWorker worker,
                                          final Consumer<WebSocketClient.ConnectionBuilder> builderConsumer,
                                          final int bufferPoolSize) {
        super(worker, builderConsumer);
        this.bufferPool = new DefaultByteBufferPool(false, bufferPoolSize);
    }

    @Override
    public Mono<Void> execute(final URI url, final HttpHeaders headers, final WebSocketHandler handler) {
        return this.executeInternal(url, headers, handler);
    }

    private Mono<Void> executeInternal(final URI url, final HttpHeaders headers, final WebSocketHandler handler) {
        final MonoProcessor<Void> completion = MonoProcessor.create();
        return Mono.fromCallable(
                () -> {
                    WebSocketClient.ConnectionBuilder builder = this.createConnectionBuilder(url);
                    List<String> protocols = this.beforeHandshake(url, headers, handler);
                    DefaultNegotiation negotiation = new DefaultNegotiation(protocols, headers, builder);
                    builder.setClientNegotiation(negotiation);
                    return builder.connect().addNotifier(
                            new IoFuture.HandlingNotifier<>() {
                                @Override
                                public void handleDone(final WebSocketChannel channel, final Object attachment) {
                                    ClosingUndertowWebSocketClient.this.handleChannel(url, handler, completion, negotiation, channel);
                                }

                                @Override
                                public void handleFailed(final IOException ex, final Object attachment) {
                                    completion.onError(new IllegalStateException("Failed to connect", ex));
                                }
                            }, null);
                })
                .then(completion);
    }

    // * * * * *
    // plug in a shared buffer pool

    /**
     * {@inheritDoc}
     */
    @Override
    protected WebSocketClient.ConnectionBuilder createConnectionBuilder(final URI url) {
        final WebSocketClient.ConnectionBuilder builder = io.undertow.websockets.client.WebSocketClient
                .connectionBuilder(this.getXnioWorker(), this.bufferPool, url);
        this.getConnectionBuilderConsumer().accept(builder);
        return builder;
    }

    @Override
    public void setPoolBufferSize(final int poolBufferSize) {
        throw new UnsupportedOperationException("The pool buffer size can only be set through the constructor," +
                "because this implementation uses a shared pool that is only created once.");
    }

    // * * * * *

    private void handleChannel(final URI url, final WebSocketHandler handler, final MonoProcessor<Void> completion,
                               final DefaultNegotiation negotiation, final WebSocketChannel channel) {

        final HandshakeInfo info = this.afterHandshake(url, negotiation.getResponseHeaders());
        final UndertowWebSocketSession session = new UndertowWebSocketSession(channel, info, this.bufferFactory, completion);

        // * * * * *
        // plug in our custom websocket handler adapter

        final UndertowWebSocketHandlerAdapter adapter = new ClosingUndertowWebSocketHandlerAdapter(session);

        // * * * * *

        channel.getReceiveSetter().set(adapter);
        channel.resumeReceives();

        handler.handle(session).subscribe(session);
    }

    private static final class DefaultNegotiation extends WebSocketClientNegotiation {

        private final HttpHeaders requestHeaders;

        private final HttpHeaders responseHeaders = new HttpHeaders();

        @Nullable
        private final WebSocketClientNegotiation delegate;

        public DefaultNegotiation(final List<String> protocols, final HttpHeaders requestHeaders,
                                  final WebSocketClient.ConnectionBuilder connectionBuilder) {

            super(protocols, Collections.emptyList());
            this.requestHeaders = requestHeaders;
            this.delegate = connectionBuilder.getClientNegotiation();
        }

        public HttpHeaders getResponseHeaders() {
            return this.responseHeaders;
        }

        @Override
        public void beforeRequest(final Map<String, List<String>> headers) {
            this.requestHeaders.forEach(headers::put);
            if (this.delegate != null) {
                this.delegate.beforeRequest(headers);
            }
        }

        @Override
        public void afterRequest(final Map<String, List<String>> headers) {
            headers.forEach(this.responseHeaders::put);
            if (this.delegate != null) {
                this.delegate.afterRequest(headers);
            }
        }
    }
}
