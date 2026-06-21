package online.hanatalk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HanaTalkApplication

fun main(args: Array<String>) {
    runApplication<HanaTalkApplication>(*args)
}
