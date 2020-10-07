package example

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Random


trait Service[A, B] {

  val name: String

  def serve(a: A): Future[B]

  def fail(a: A): Future[B] = {
    println(s"\t\t\t>$name service is failing to: $a")
    throw new RuntimeException()
  }

  def success(a: A)(f: A => B): Future[B] = {
    println(s"\t\t\t>$name service replies to $a")
    Future.successful(f(a))
  }
}

case class ConsistentService[A, B](name: String)(f: A => B) extends Service[A, B] {
  override def serve(a: A): Future[B] = success(a)(f)
}

case class FailingService[A, B](failAfter: Int, name: String)(f: A => B) extends Service[A, B] {

  val atomicCounter = new AtomicInteger(0)

  def serve(a: A): Future[B] = {
    if (atomicCounter.incrementAndGet() < failAfter) {
      success(a)(f)
    } else {
      fail(a)
    }
  }
}

case class FailSwitchService[A, B](failAfter: Int, failFor: Int, name: String)(f: A => B) extends Service[A, B] {

  val fas = FailingService[A, B](failAfter, name)(f)

  def serve(a: A): Future[B] = {
    if (fas.atomicCounter.get() >= (failAfter + failFor)) {
      fas.atomicCounter.set(0)
    }
    fas.serve(a)
  }
}

case class RatedFailingService[A, B](failPercent: Int, name: String)(f: A => B) extends Service[A, B] {

  def serve(a: A): Future[B] = {
    if (Math.random() * 100 <= failPercent) {
      fail(a)
    } else {
      success(a)(f)
    }
  }

}

case class SlowService[A, B](duration: Duration, name: String)(f: A => B) extends Service[A, B] {

  override def serve(a: A): Future[B] = {
    Thread.sleep(duration.toMillis)
    success(a)(f)
  }
}

case class ChaoticService[A, B](maxDuration: Duration, failPercent: Int, name: String)(f: A => B) extends Service[A, B] {

  private val fService = RatedFailingService(failPercent, name)(f)

  override def serve(a: A): Future[B] = {
    val sleepTime = Random.nextLong(maxDuration.toMillis)
    Thread.sleep(sleepTime)
    fService.serve(a)
  }
}