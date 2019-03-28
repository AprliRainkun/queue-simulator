package style95

import akka.actor.{Actor, ActorRef, Props}
import style95.Container._
import style95.QueueSimulator.ConsultScaler
import style95.StatusLogger.ActivationRecord
import style95.scaler._

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.language.postfixOps

object QueueSimulator {
  final case object ConsultScaler

  def props(buildScaler: (FiniteDuration, FiniteDuration, ActorRef) => Scaler,
            logger: ActorRef,
            checkInterval: FiniteDuration,
            containerProps: ContainerProperty): Props =
    Props(
      new QueueSimulator(buildScaler, logger, checkInterval, containerProps))
}

class QueueSimulator(
    buildScaler: (FiniteDuration, FiniteDuration, ActorRef) => Scaler,
    logger: ActorRef,
    checkInterval: FiniteDuration,
    containerProps: ContainerProperty)
    extends Actor {

  import context.{dispatcher, system}

  private val scaler =
    buildScaler(containerProps.initialDelay, containerProps.execTime, logger)

  private val simStart = System.nanoTime()
  private var queue = Queue.empty[ActivationMessage]
  private var existing = Map.empty[ActorRef, ContainerStatus]
  private var creating = Set.empty[ActorRef]

  private var inSinceLastTick = 0
  private var outSinceLastTick = 0

  private var scheduledNum = 0
  private var averageLatency = Double.NaN

  private var averageInvokeTime = 0D
  private var completedActivations = 0L

  system.scheduler.schedule(0 seconds, checkInterval) {
    self ! ConsultScaler
  }

  override def receive: Receive = {
    case msg: ActivationMessage =>
      inSinceLastTick += 1
      queue = queue.enqueue(msg)
      tryRunActions()
    case ContainerCreated =>
      creating -= sender
      existing += sender -> ContainerStatus(true)
      tryRunActions()
    case WorkDone(msg @ ActivationMessage(requester, start, invoked)) =>
      val invokeTime = System.nanoTime() - invoked
      outSinceLastTick += 1
      existing += sender -> ContainerStatus(true)
      requester ! msg

      completedActivations += 1
      averageInvokeTime += 1.0 / completedActivations * (invokeTime - averageInvokeTime)

      logger ! ActivationRecord(elapsed, invoked - start, invokeTime)

      tryRunActions()
    case ConsultScaler =>
      logger ! StatusLogger.QueueSnapshot(elapsed,
                                          inSinceLastTick,
                                          outSinceLastTick,
                                          queue.size,
                                          existing.size,
                                          creating.size,
                                          averageLatency)

      scaler.decide(
        DecisionInfo(elapsed,
                     inSinceLastTick,
                     outSinceLastTick,
                     averageInvokeTime nanos,
                     existing.size,
                     creating.size,
                     queue.size)) match {
        case AddContainer(number) =>
          println(s"create $number containers")
          (1 to number) foreach { _ =>
            val container =
              context.actorOf(Container.props(self, containerProps))
            creating += container
          }
        case RemoveContainer(number) => ???
        case NoOp                    =>
      }

      inSinceLastTick = 0
      outSinceLastTick = 0
  }

  private def tryRunActions(): Unit = {
    val idles = idleContainers.iterator

    while (queue.nonEmpty && idles.hasNext) {
      val (msg, newQueue) = queue.dequeue
      val idle = idles.next()

      queue = newQueue
      existing += idle -> ContainerStatus(false)

      val latency = (System.nanoTime() - msg.userStart) / 1e6
      scheduledNum += 1
      if (averageLatency.isNaN) { averageLatency = 0.0 }
      averageLatency += 1.0 / scheduledNum * (latency - averageLatency)

      idle ! msg.invokeAt(System.nanoTime())
    }
  }

  private def idleContainers =
    existing
      .filter {
        case (_, ContainerStatus(ready)) => ready
      }
      .map {
        case (actor, _) => actor
      }

  private def elapsed: Long = System.nanoTime() - simStart

}
