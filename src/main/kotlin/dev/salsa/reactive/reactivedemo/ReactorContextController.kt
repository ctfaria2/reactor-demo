package dev.salsa.reactive.reactivedemo

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.util.context.Context

@RestController
class ReactorContextController {

    @GetMapping("/api/context/default")
    fun defaultContext(): Mono<ContextResponse> {
        return Mono.subscriberContext().map { createDemoResponse(it) }
    }

    @GetMapping("/api/context/populated")
    fun populatedContext(): Mono<ContextResponse> {
        return Mono.subscriberContext()
            .map { context ->
                createDemoResponse(
                    context.put("key", "value")
                )
            }
    }

    @GetMapping("/api/context/populated-run-blocking")
    fun populatedContextRunBlockingNoContext(): Mono<ContextResponse> {
        return Mono.subscriberContext()
            .map { context ->
                context.put("key", "value")
            }.then(
                Mono.just(
                    runBlocking {
                        //Here context will be empty. This is because no context was passed into `runBlocking`
                        createDemoResponse(getReactorContextFromCoroutine())

                    }
                )
            )
    }

    @GetMapping("/api/context/populated-run-blocking-init")
    fun populatedContextRunBlockingWithNewContext(): Mono<ContextResponse> {
        return Mono.subscriberContext()
            .map { context ->
                context.put("key", "value")
            }.then(
                Mono.just(
                    runBlocking(
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

    @GetMapping("/api/context/populated-run-blocking-bridged")
    fun populatedContextBridgedToRunBlocking(): Mono<ContextResponse> {
        val reactorContext = runBlocking {
            Mono.subscriberContext()
                .map { context ->
                    context.put("key", "value")
                }.awaitSingle()
        }

        return Mono.just(
                runBlocking(
                    context = ReactorContext(reactorContext)
                ) {
                    // Here context will have the value that was set up and passed into runBlocking. In this case
                    // it is the original value since we passed that context through
                    createDemoResponse(getReactorContextFromCoroutine())
                }
            )
    }

    private fun CoroutineScope.getReactorContextFromCoroutine(): Context {
        val reactorContextWrapper = coroutineContext[ReactorContext]
        return reactorContextWrapper?.context ?: Context.empty()
    }
}

fun createDemoResponse(context: Context): ContextResponse {
    return if (context.isEmpty) {
        ContextResponse.Empty()
    } else {
        ContextResponse.HasValue(context)
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
