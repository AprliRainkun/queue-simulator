package style95.generator

import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.junit.JUnitRunner
import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.abs

@RunWith(classOf[JUnitRunner])
class SingleBehaviorActorSpec
    extends TestKit(ActorSystem("SingleBehaviorActorSpec"))
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = shutdown(system)

  "A single behavior actor" should {
    "produce statistically verifiable result for maintain TPS behavior" in {
      import AverageTpsObserver.AverageTps

      val epsilon = 0.01
      val targetTps = 20
      val sampleSecs = 5.0
      val probe = TestProbe()

      val observer = system.actorOf(AverageTpsObserver.props(probe.ref),
                                    "average-tps-observer")

      val _ = system.actorOf(Maintain(targetTps) in sampleSecs.seconds against observer,
                     "maintain-actor")
      val AverageTps(obTps, obDuration) =
        probe.expectMsgClass(classOf[AverageTps])

      (abs(obTps - targetTps) / targetTps) should be <= epsilon
      (abs(obDuration - sampleSecs) / sampleSecs) should be <= epsilon
    }
  }
}
