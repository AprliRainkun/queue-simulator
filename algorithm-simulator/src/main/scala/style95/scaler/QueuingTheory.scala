package style95.scaler

import akka.actor.ActorRef

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.math.{ceil, max}

class QueuingTheory(creatingHint: Int,
                    invokeInterval: Int,
                    alpha: Double,
                    slo: FiniteDuration,
                    utilization: Double,
                    idleTimeout: FiniteDuration,
                    logger: ActorRef)
    extends Scaler {
  // TA + M/M/1 + Rule
  // scaling out according to queuing theory;
  // scaling in by rules
  require(slo > 0.second, "latency requirement must be greater than zero")
  require(utilization < 1, "utilization lower bound must be smaller than one")

  private var s = 0D
  private var idleBuffer = Queue.empty[Long]

  override def decide(info: DecisionInfo): Decision = {
    val DecisionInfo(elapse, in, _, invokeTime, existing, creating, _) = info
    // 1. Parameters
    //   a. Prediction horizon: the predicted system state that are used to model the queue.
    // 2. Analysis stage
    //   a. use time series analysis to predict the load status
    //   b. estimate the service rate (aggregated service rate as if there is only one container)
    //   c. estimate the container creating time
    // 3. Plan stage
    //   a. we know the predicted load in the prediction horizon
    //   b. then make a conservative guess of the number of would-be existing containers
    //   c. calculate needed the number of containers to satisfy the latency SLO
    //   d. if should scale up:
    //        remove container from standing-by list, then emit AddContainer
    //      if should scale down:
    //        add container to standing-by list
    //   e. emit RemoveContainer if there are some standing-by containers that have timed out
    val forwardSteps = ceil(creatingHint.toDouble / invokeTime.toMillis).toInt
    val forecast =
      forecastTps(in / (invokeInterval.toDouble / 1e3), forwardSteps)
    // assume all initialing containers will be ready
    val readyContainer = existing + creating
    val lowerBound = deriveRequired(forecast, invokeTime)
    val upperBound = utilizationBound(forecast, invokeTime)

    var containerDelta = 0

    if (readyContainer < lowerBound) {
      containerDelta += wakeUpIdle(lowerBound - readyContainer)
    }
    if (lowerBound <= upperBound) {
      if (readyContainer > upperBound) {
        markAsIdle(readyContainer - upperBound)
      }
    } else {
      println(
        s"utilization upper-bound($upperBound) not applied. The latency lower-bound($lowerBound) takes precedence.")
    }

    containerDelta -= evictTimeout()
    containerDelta match {
      case c if c > 0 => AddContainer(c)
      case c if c < 0 => RemoveContainer(c)
      case _          => NoOp
    }
  }

  private def forecastTps(currentTps: Double, steps: Int): Double = {
    s += alpha * (currentTps - s)
    s
  }

  private def deriveRequired(tps: Double, invokeTime: FiniteDuration): Int = {
    val serveRate = 1e9D / invokeTime.toNanos
    val sloSecs = slo.toNanos / 1e9D
    ceil((1 + tps * sloSecs) / (serveRate * sloSecs)).toInt
  }

  private def utilizationBound(tps: Double, invokeTime: FiniteDuration): Int = {
    val serveRate = 1e9D / invokeTime.toNanos
    ceil(tps / (serveRate * utilization)).toInt
  }

  private def wakeUpIdle(number: Int): Int = {
    val spill = max(0, number - idleBuffer.size)
    idleBuffer = idleBuffer.drop(number)
    spill
  }

  private def markAsIdle(number: Int): Unit = {
    idleBuffer = idleBuffer ++ (1 to number).map(_ => System.nanoTime())
  }

  private def evictTimeout(): Int = {
    val count =
      idleBuffer.count(t => (System.nanoTime() - t).nanos >= idleTimeout)
    idleBuffer = idleBuffer.drop(count)
    count
  }
}
