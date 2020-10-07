package example

import java.util.concurrent.atomic.AtomicInteger
import java.util.function
import java.util.function.Supplier

import io.github.resilience4j.circuitbreaker._
import io.vavr.CheckedFunction1
import io.vavr.control.Try

import scala.concurrent.Future
import scala.concurrent.duration._

object Hello extends App {

  val failingService = FailSwitchService[String, Int](5, 3, "main")(_.length)
  val slowService = SlowService[String, Int](1500.millis, "slow")(_.length)
  val fallbackService = ConsistentService[String, Int]("fallback")(_.length)

  val circuitBreakerConfig = CircuitBreakerConfig.custom()
//    .failureRateThreshold(20.0f)
    .permittedNumberOfCallsInHalfOpenState(2)
    .slidingWindowSize(10)
    .minimumNumberOfCalls(10)
    .slowCallRateThreshold(10f)
    .slowCallDurationThreshold(java.time.Duration.ofMillis(100))
    .waitDurationInOpenState(java.time.Duration.ofMillis(2000))
    .build()

  val circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig)

  val circuitBreaker = circuitBreakerRegistry.circuitBreaker("circuit1")

  val atomicCounter = new AtomicInteger(0)
  circuitBreaker.getEventPublisher
    .onFailureRateExceeded(e => s"\t:Failure rate exceeded: $e")
    .onStateTransition(e => s"\t:State transition: $e")
//    .onSuccess(e => println(s"\t:Success ${atomicCounter.incrementAndGet()}"))
    .onError(e => {
      println(s"\t:Error: $e")
      atomicCounter.set(0)
    })

  val decoratedService = CircuitBreaker.decorateFunction[String, Future[Int]](circuitBreaker, callMain)
  val decoratedSlowService = CircuitBreaker.decorateFunction[String, Future[Int]](circuitBreaker, callSlowService)

  def callMain(input: String) = {
    failingService.serve(input)
  }
  def callFallback(input: String) = {
    fallbackService.serve(input)
  }
  def callSlowService(input: String) = {
    slowService.serve(input)
  }

  for (j <- 1 to 2) {
    for (i <- 1 to 20) {
      Thread.sleep(300)

//      try {
//        decoratedService(i.toString)
//      } catch {
//        case e: CallNotPermittedException => println("call directly rejected")
//        case e: RuntimeException => println("service returs an error")
//        case e: Throwable => {
//          println("unexpected exception")
//          e.printStackTrace()
//        }
//      }

      val input = j.toString.concat("_").concat(i.toString)
      Try.ofSupplier(() => decoratedService(input))
        .recover(throwable=>decoratedSlowService(input))
        .recover(throwable => callFallback(input))
    }
    println
  }
}
