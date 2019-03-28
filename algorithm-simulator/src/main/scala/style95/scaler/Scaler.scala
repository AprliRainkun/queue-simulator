package style95.scaler

import scala.concurrent.duration.FiniteDuration

final case class DecisionInfo(
    elapse: Long,
    in: Int, // number of new requests since last tick
    out: Int, // number of consumed requests since last tick
    invokeTime: FiniteDuration, // average invoking time in milliseconds
    existing: Int, // created containers, busy or idle
    creating: Int, // containers that are being initialized
    queued: Int // number of waiting requests
)

sealed trait Decision

final case class AddContainer(number: Int) extends Decision
final case class RemoveContainer(number: Int) extends Decision
case object NoOp extends Decision

trait Scaler {
  def decide(info: DecisionInfo): Decision
}
