package ru.spasitel.factorioautoplanner.data.auto

data class BlueprintDTO(
    val blueprint: Blueprint
)

data class Blueprint(
    val entities: List<Entity>,
    val icons: List<Icon>,
    val item: String,
    val version: Long
)

data class Entity(
    val connections: Connections,
    val control_behavior: ControlBehavior,
    val direction: Int,
    val entity_number: Int,
    val name: String,
    val neighbours: List<Int>,
    val position: Position,
    val station: String
)

data class Icon(
    val index: Int,
    val signal: Signal
)

data class Connections(
    val `1`: X1,
    val `2`: X2
)

data class ControlBehavior(
    val arithmetic_conditions: ArithmeticConditions,
    val circuit_condition: CircuitCondition,
    val circuit_mode_of_operation: Int,
    val decider_conditions: DeciderConditions,
    val read_from_train: Boolean,
    val set_trains_limit: Boolean,
    val train_stopped_signal: TrainStoppedSignal,
    val trains_limit_signal: TrainsLimitSignal
)

data class Position(
    val x: Double,
    var y: Double
)

data class X1(
    val green: List<Green>,
    val red: List<Red>
)

data class X2(
    val green: List<Green>,
    val red: List<Red>
)

data class Green(
    val circuit_id: Int,
    val entity_id: Int
)

data class Red(
    val circuit_id: Int,
    val entity_id: Int
)

data class ArithmeticConditions(
    val first_constant: Int,
    val first_signal: FirstSignal,
    val operation: String,
    val output_signal: OutputSignal,
    val second_constant: Int,
    val second_signal: SecondSignal
)

data class CircuitCondition(
    val comparator: String,
    val first_signal: FirstSignal,
    val second_signal: SecondSignal
)

data class DeciderConditions(
    val comparator: String,
    val copy_count_from_input: Boolean,
    val first_signal: FirstSignal,
    val output_signal: OutputSignal,
    val second_signal: SecondSignal
)

data class TrainStoppedSignal(
    val name: String,
    val type: String
)

data class TrainsLimitSignal(
    val name: String,
    val type: String
)

data class FirstSignal(
    val name: String,
    val type: String
)

data class OutputSignal(
    val name: String,
    val type: String
)

data class SecondSignal(
    val name: String,
    val type: String
)

data class Signal(
    val name: String,
    val type: String
)