package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.model.A3UISurface
import a3.core.model.CapabilityGraph
import a3.core.model.Goal
import a3.core.model.Intent
import a3.core.model.Plan
import a3.core.model.TrustGrant
import a3.core.model.TrustTier
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.policy.Policy
import a3.core.policy.PolicyDecision
import a3.core.runtime.ExecutionResult
import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import a3.intent.IntentProvider
import a3.intent.RuleIntentProvider
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostUi(
    val intent: Intent? = null,
    val plan: Plan? = null,
    val surface: A3UISurface? = null,
    val output: RenderedOutput? = null,
    val belief: BeliefState = BeliefState(),
    val trustHold: Boolean = false,
    val rollbackVisible: Boolean = false,
    val lastExecution: ExecutionResult? = null,
    val stage: String = RendererContext.STAGE_PRONTO
)

class A3HostViewModel(
    private val world: BeliefWriter,
    private val planner: DeterministicPlanner,
    private val runtime: Runtime,
    private val policy: Policy,
    private val intentProvider: IntentProvider,
    private val compiler: DeterministicA3UICompiler,
    private val interpreter: A3UIInterpreter,
    private val prefetch: HostPrefetch,
    private val clock: InstantSource,
    private val graph: CapabilityGraph,
    private val goalFor: (String) -> Goal,
    private val executor: Executor
) {
    private val _ui = MutableStateFlow(HostUi())
    val ui: StateFlow<HostUi> = _ui.asStateFlow()

    private var prefetchSurface: A3UISurface? = null
    private var prefetchHit: Boolean = false

    init {
        start()
    }

    fun start() {
        val inferred = intentProvider.infer(DemoFixtures.intentContext())
        val goal = goalFor(inferred.id)
        val planned = planner.plan(goal, world.committed, graph, clock.now())
        val plan = (planned as PlanResult.Success).plan
        val presentation = DemoFixtures.presentation()
        val surface = compiler.compile(DemoFixtures.projection(), presentation)
        val listening = DemoFixtures.rendererContext(RendererContext.STAGE_ASCOLTO)
        val output = interpreter.interpret(surface, presentation, listening)
        val prepared = compiler.compilePrefetch(DemoFixtures.candidate(world.committed.version))
        prefetchSurface = prepared
        prefetchHit = prefetch.composeOffscreen(
            prepared,
            listening,
            world.committed.version
        ) != null
        _ui.value = HostUi(
            intent = inferred,
            plan = plan,
            surface = surface,
            output = output,
            belief = world.committed,
            stage = hostStage(trustHold = false, executed = null, prefetchHit = prefetchHit)
        )
    }

    fun interpret01(surface: A3UISurface): RenderedOutput =
        interpreter.interpret(surface, DemoFixtures.rendererContext())

    fun interpret02(surface: A3UISurface): RenderedOutput =
        interpreter.interpret(surface, DemoFixtures.presentation(), DemoFixtures.rendererContext())

    fun prefetchHit(): RenderedOutput? {
        val surface = prefetchSurface ?: return null
        return prefetch.composeOffscreen(
            surface,
            DemoFixtures.rendererContext(),
            world.committed.version
        ) ?: prefetch.get(surface.prefetch!!.candidateRef, world.committed.version)
    }

    fun prefetchGet(): RenderedOutput? {
        val surface = prefetchSurface ?: return null
        return prefetch.get(surface.prefetch!!.candidateRef, world.committed.version)
    }

    fun onAction(name: String) {
        if (name != "confirm") return
        val plan = _ui.value.plan ?: return
        when (decisionFor(plan)) {
            PolicyDecision.CONFIRM -> {
                _ui.value = _ui.value.copy(
                    trustHold = true,
                    stage = RendererContext.STAGE_APPROVA
                )
            }
            PolicyDecision.ALLOW -> execute(grants = emptyMap())
            PolicyDecision.DENY -> { }
        }
    }

    fun approveTrust() {
        val plan = _ui.value.plan ?: return
        val grants = plan.steps
            .filter { it.trustTier == TrustTier.IRREVERSIBLE }
            .associate { step ->
                step.capability to TrustGrant(
                    "tg_${step.capability}",
                    step.capability,
                    TrustTier.IRREVERSIBLE,
                    true,
                    clock.now().plusSeconds(300)
                )
            }
        execute(grants)
    }

    private fun decisionFor(plan: Plan): PolicyDecision {
        val irreversible = plan.steps.any { it.trustTier == TrustTier.IRREVERSIBLE }
        return if (irreversible) {
            policy.evaluate(TrustTier.IRREVERSIBLE, false)
        } else {
            policy.evaluate(TrustTier.PREPARE, true)
        }
    }

    private fun execute(grants: Map<String, TrustGrant>) {
        val plan = _ui.value.plan ?: return
        val result = runtime.execute(
            plan,
            world,
            graph.capabilities,
            executor,
            clock.now(),
            grants
        )
        val presentation = DemoFixtures.presentation()
        val surface = compiler.compile(DemoFixtures.projection(), presentation)
        val working = DemoFixtures.rendererContext(RendererContext.STAGE_LAVORO)
        val output = interpreter.interpret(surface, presentation, working)
        _ui.value = _ui.value.copy(
            trustHold = false,
            rollbackVisible = result.rolledBack,
            lastExecution = result,
            belief = result.state,
            surface = surface,
            output = output,
            stage = hostStage(trustHold = false, executed = result, prefetchHit = prefetchHit)
        )
    }

    private fun hostStage(
        trustHold: Boolean,
        executed: ExecutionResult?,
        prefetchHit: Boolean
    ): String = when {
        trustHold -> RendererContext.STAGE_APPROVA
        executed != null -> RendererContext.STAGE_LAVORO
        prefetchHit -> RendererContext.STAGE_ASCOLTO
        else -> RendererContext.STAGE_PRONTO
    }

    companion object {
        fun train(
            executor: Executor = DemoFixtures.matchingExecutor(),
            world: BeliefWriter = BeliefWriter()
        ): A3HostViewModel {
            val clock = DemoFixtures.clock
            return A3HostViewModel(
                world = world,
                planner = DeterministicPlanner(),
                runtime = Runtime(Policy(), TrustGate()),
                policy = Policy(),
                intentProvider = RuleIntentProvider(0.8),
                compiler = DeterministicA3UICompiler(clock, SequentialIdGenerator()),
                interpreter = A3UIInterpreter(),
                prefetch = HostPrefetch(),
                clock = clock,
                graph = DemoFixtures.trainGraph(),
                goalFor = { DemoFixtures.trainGoal(it) },
                executor = executor
            )
        }

        fun reversible(
            executor: Executor = DemoFixtures.matchingExecutor(),
            world: BeliefWriter = BeliefWriter()
        ): A3HostViewModel {
            val clock = DemoFixtures.clock
            return A3HostViewModel(
                world = world,
                planner = DeterministicPlanner(),
                runtime = Runtime(Policy(), TrustGate()),
                policy = Policy(),
                intentProvider = RuleIntentProvider(0.8),
                compiler = DeterministicA3UICompiler(clock, SequentialIdGenerator()),
                interpreter = A3UIInterpreter(),
                prefetch = HostPrefetch(),
                clock = clock,
                graph = DemoFixtures.reversibleGraph(),
                goalFor = { DemoFixtures.reversibleGoal(it) },
                executor = executor
            )
        }
    }
}
