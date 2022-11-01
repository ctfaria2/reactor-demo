package dev.salsa.reactive.reactivedemo

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.util.context.Context
import java.util.concurrent.CompletableFuture

@RestController
class ReactorContextController {

    @GetMapping("/api/context/default")
    fun defaultContext(): Mono<ContextResponse> {
        return Mono.subscriberContext().map { createDemoResponse(it) }
    }

    @GetMapping("/api/context/populated")
    fun populatedContext(): Mono<ContextResponse> {
        return monoWithReactorContextInitialized()
            .map { context ->
                createDemoResponse(
                    context.put("key", "value")
                )
            }
    }

    @GetMapping("/api/context/populated-run-blocking")
    fun populatedContextRunBlockingNoContext(): Mono<ContextResponse> {
        return monoWithReactorContextInitialized().then(
                Mono.just(
                    runBlocking {
                        //Here context will be empty. This is because no context was passed into `runBlocking`
                        createDemoResponse(getReactorContextFromCoroutine())
                    }
                )
            )
    }

    @GetMapping("/api/context/populated-run-blocking-bridged")
    fun populatedContextBridgedToRunBlocking(): Mono<ContextResponse> {
        val reactorContext = runBlocking { monoWithReactorContextInitialized().awaitSingle() }
        return Mono.just(
            runBlocking(
                // This is the 'trick' to start a coroutine with the existing reactor context. You need to 'get' the
                // context beforehand.
                context = ReactorContext(reactorContext)
            ) {
                // Here context will have the value that was set up and passed into runBlocking. In this case
                // it is the original value since we passed that context through
                createDemoResponse(getReactorContextFromCoroutine())
            }
        )
    }

    @GetMapping("/api/context/populated-run-blocking-init")
    fun populatedContextRunBlockingWithNewContext(): Mono<ContextResponse> {
        return monoWithReactorContextInitialized().then(
                Mono.just(
                    runBlocking(
                        // In this case, you can see why this 'trick' is not optimal. You could give the coroutine
                        // any context. It gives the developer a lot of power to do even more tricks
                        // like faking the context
                        context = ReactorContext(
                            context = Context.of("key", "otherValue")
                        )
                    ) {
                        //Here context will have the value that was passed into runBlocking. We still lose the original
                        //context but we can see how runBlocking could be initialized with a context
                        createDemoResponse(getReactorContextFromCoroutine())
                    }
                )
            )
    }

    @GetMapping("/api/context/populated-completable-future")
    fun populatedContextCompletableFuture(): Mono<ContextResponse> {
        return monoWithReactorContextInitialized().then(
                Mono.just(
                    // Creating a completable future. The subscriber context we will end up with will be empty because
                    // the context is available in the completable future (empty reactor context)
                    CompletableFuture<Mono<ContextResponse>>().completeAsync {
                        Mono.subscriberContext().map { createDemoResponse(it) }
                    }.toMono().flatMap { it }
                ).flatMap { it }
            )
    }

    @OptIn(DelicateCoroutinesApi::class)
    @GetMapping("/api/context/populated-completable-future-bridged")
    fun populatedContextBridgedToCompletableFuture(): Mono<ContextResponse> {
        return monoWithReactorContextInitialized().then(
            Mono.just(
                Mono.subscriberContext().flatMap { context ->
                    GlobalScope.future(
                        // GlobalScope is a kotlin coroutine utility to create a completable future. It is the best way
                        // we found to bridge Reactor and Java 8 futures. Since futures don't have the same context
                        // concept the coroutine context is used so that when any new Mono is created the subscriber
                        // context is populated as expected
                        context = ReactorContext(context)
                    ) {
                        Mono.subscriberContext().map { createDemoResponse(it) }
                    }.toMono().flatMap { it }
                }
            ).flatMap { it }
        )
    }

    private fun CoroutineScope.getReactorContextFromCoroutine(): Context {
        val reactorContextWrapper = coroutineContext[ReactorContext]
        return reactorContextWrapper?.context ?: Context.empty()
    }
}

private fun createDemoResponse(context: Context): ContextResponse {
    return if (context.isEmpty) {
        ContextResponse.Empty()
    } else {
        ContextResponse.HasValue(context)
    }
}

/**
 * Take the subscriber context and populate it with a generic key and value
 */
private fun monoWithReactorContextInitialized(): Mono<Context> {
    return Mono.subscriberContext()
        .map { context ->
            context.put("key", "value")
        }
}

@JsonSerialize(using = ToStringSerializer::class)
open class ContextResponse {
    class Empty: ContextResponse() {
        override fun toString(): String {
            return "Context is empty"
        }
    }
    class HasValue(private val context: Context): ContextResponse() {
        override fun toString(): String {
            return "Context has value: " + context["key"]
        }
    }
}
