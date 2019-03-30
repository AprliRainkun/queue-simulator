package style95.scaler

import akka.actor.ActorRef
import style95.StatusLogger.PredictedTps

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.math.{ceil, max}

class QueuingTheory(creatingHint: FiniteDuration,
                    consultInterval: FiniteDuration,
                    logger: ActorRef,
                    alpha: Double,
                    slo: FiniteDuration,
                    idleTimeout: FiniteDuration,
) extends Scaler {
  // TA + M/M/1 + Rule
  // scaling out according to queuing theory;
  // scaling in by rules
  require(slo > 0.second, "latency requirement must be greater than zero")

  private var s = 0D
  private var idleBuffer = Queue.empty[(ActorRef, Long)]

  override def decide(info: DecisionInfo): Decision = {
    val DecisionInfo(elapse, in, _, invokeTime, existing, creating, queued) =
      info
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
    val nSteps = forwardSteps(creatingHint, consultInterval)
    val forecast =
      forecastTps(in.toDouble / (consultInterval.toMillis / 1e3), nSteps)

    logger ! PredictedTps(elapse + nSteps * consultInterval.toNanos, forecast)
    // assume all initialing containers will be ready
    val readyContainer = existing.size + creating.size
    val lowerBound = deriveRequired(forecast, invokeTime)
    //val upperBound = utilizationBound(forecast, invokeTime)

    println(s"forecast: $forecast ,container: $readyContainer, lb: $lowerBound")

    val newAlloc = wakeUpIdle(max(lowerBound - readyContainer, 0))
    if (newAlloc > 0) {
      // scaling out
      AddContainer(newAlloc)
    } else if (queued == 0) {
      // scaling in
      markAsIdle(max(readyContainer - lowerBound, 0), existing)
      evictTimeout() match {
        case Nil     => NoOp
        case victims => RemoveContainer(victims)
      }
    } else {
      NoOp
    }
  }

  private def forwardSteps(creatingHint: FiniteDuration,
                           consultInterval: FiniteDuration): Int =
    ceil(creatingHint.toMillis.toDouble / consultInterval.toMillis).toInt

  private def forecastTps(currentTps: Double, steps: Int): Double = {
    s += alpha * (currentTps - s)
    s
  }

  private def deriveRequired(tps: Double, invokeTime: FiniteDuration): Int = {
    if (tps <= 0.01) { // kind of arbitrary
      0
    } else {
      val serveRate = 1e9D / invokeTime.toNanos
      val sloSecs = slo.toNanos / 1e9D
      ceil((1 + tps * sloSecs) / (serveRate * sloSecs)).toInt
    }
  }

  private def wakeUpIdle(number: Int): Int = {
    val spill = max(number - idleBuffer.size, 0)
    idleBuffer = idleBuffer.dropRight(number)
    spill
  }

  private def markAsIdle(number: Int, candidates: List[ActorRef]): Unit = {
    val notMarked = candidates.filter(a =>
      !idleBuffer.exists {
        case (buffered, _) => a == buffered
    })
    idleBuffer = idleBuffer ++ notMarked
      .take(number)
      .map(a => (a, System.nanoTime()))
  }

  private def evictTimeout(): List[ActorRef] = {
    val num = idleBuffer.count {
      case (_, t) => (System.nanoTime() - t).nanos >= idleTimeout
    }
    val (evicted, remaining) = idleBuffer.splitAt(num)
    idleBuffer = remaining
    evicted.map(_._1).toList
  }
}
