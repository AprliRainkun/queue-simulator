package style95

object Scaler {
  final case class DecideInfo(
      income: Int, // new requests since last tick
      outcome: Int, // consumed requests since last tick
      existing: Int, // created containers, busy or idle
      inProgress: Int, // containers that are being initialized
      current: Int)

  sealed trait Decision

  final case class AddContainer(number: Int) extends Decision
  final case object NoOp extends Decision
}

class Scaler(containerLimit: Int) {
  import Scaler._

  private var tick = 0
  private var totalIncome = 0
  private var totalConsumption = 0
  private var perConConsumptionPerTick = 0.0
  private var maxConsumptionInTick = -1.0

  def decide(info: DecideInfo): Decision = {
    var DecideInfo(income, outcome, existing, inProgress, current) = info

    totalIncome += income
    totalConsumption += outcome

    if (existing == 0 && inProgress == 0) {
      return AddContainer(1)
    }

    if (totalConsumption > 0) {
      tick += 1
      perConConsumptionPerTick = totalConsumption.toDouble / existing / tick
    }

    maxConsumptionInTick = math.max(maxConsumptionInTick, outcome)
    val expectedTpt = perConConsumptionPerTick * (existing + inProgress)

    println(
      s"perConConsumption: $perConConsumptionPerTick, expectedTpt: $expectedTpt, maxConsumptionInTick: $maxConsumptionInTick")

    val number =
      if (income >= expectedTpt && current > maxConsumptionInTick && expectedTpt != 0) {

        val criteria = (income / perConConsumptionPerTick) - existing - inProgress
        val number = math.ceil(criteria).toInt
        println(
          s"criteria: $criteria = ($income / $perConConsumptionPerTick) - $existing - $inProgress")
        number
      } else {
        0
      }

    if (number > 0) {
      AddContainer(number)
    } else {
      NoOp
    }
  }
}