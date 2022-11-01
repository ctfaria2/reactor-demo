package dev.salsa.reactive.reactivedemo

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

@RestController
class ReactorDataTypesController {

    @GetMapping("/api/types/mono-no-wait")
    fun mono(): Mono<String> {
        //mono is a reactive type that represents a list of 1 or 0 items.
        return Mono.just("I am a value")
            .map { string ->
                "$string and i was changed"
            }
    }

    //Same as above but using suspend
    @GetMapping("/api/types/mono-wait")
    suspend fun waitForMono(): String {
        val mono = Mono.just("I am a value")
        // awaitSingle is a non-blocking function that bridges from a mono to a more imperative style code. It allows us
        // to work with the actual result objects instead of having to manipulate them through the publisher interface
        // like we are doing above
        val string = mono.awaitSingle()
        return "$string and i was changed in a nicer way"
    }

    @GetMapping("/api/types/flux-no-wait")
    fun flux(): Flux<String> {
        //flux is a reactive type that represents a list of 0 to many items.
        return listOf(
            "Steve",
            "Mary",
            "Peter"
        ).toFlux()
    }

    @GetMapping("/api/types/flux-wait")
    suspend fun waitForFlux(): List<String> {
        val fluxList = listOf(
        "Steve",
        "Mary",
        "Peter"
        ).toFlux()
        // This is the way to non-blocking wait for a flux and use it as if it were just a Kotlin list. It first converts
        // it to a Kotlin Flow data type. Which is similar to a flux. Then we can bridge that back into a list
        return fluxList.asFlow().toList()
    }
}