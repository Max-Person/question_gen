package its.questions.gen.strategies

import its.model.expressions.Literal
import its.model.expressions.literals.BooleanLiteral
import its.model.nodes.*
import its.model.nodes.visitors.DecisionTreeBehaviour
import its.questions.inputs.TemplatingUtils._static.asNextStep
import its.questions.inputs.TemplatingUtils._static.description
import its.questions.inputs.TemplatingUtils._static.explanation
import its.questions.inputs.TemplatingUtils._static.nextStepBranchResult
import its.questions.inputs.TemplatingUtils._static.nextStepExplanation
import its.questions.inputs.TemplatingUtils._static.nextStepQuestion
import its.questions.inputs.TemplatingUtils._static.question
import its.questions.inputs.TemplatingUtils._static.text
import its.questions.gen.states.*
import its.questions.gen.visitors.GetPossibleJumps._static.getPossibleJumps
import its.questions.gen.visitors.LiteralToString._static.toAnswerString
import its.questions.gen.visitors.getAnswer
import its.questions.gen.visitors.isTrivial
import its.questions.inputs.LearningSituation
import java.util.*

object SequentialStrategy : QuestioningStrategyWithInfo<SequentialStrategy.SequentialAutomataInfo>{
    data class SequentialAutomataInfo(
        val nodeStates : MutableMap<DecisionTreeNode, QuestionState>,
        val preNodeStates : MutableMap<DecisionTreeNode, QuestionState>,
    )

    override fun buildWithInfo(branch: ThoughtBranch) : QuestioningStrategyWithInfo.StrategyOutput<SequentialAutomataInfo> {
        val build = SequentialAutomataBuilder()
        val initState = branch.use(build)
        val automata = QuestionAutomata(initState)
        return QuestioningStrategyWithInfo.StrategyOutput(automata, SequentialAutomataInfo(build.nodeStates, build.preNodeStates))
    }


    private class SequentialAutomataBuilder : DecisionTreeBehaviour<QuestionState> {
        val nodeStates = mutableMapOf<DecisionTreeNode, QuestionState>()
        val preNodeStates = mutableMapOf<DecisionTreeNode, QuestionState>()
        private val branchDiving : Stack<ThoughtBranch> = Stack()
        private val currentBranch
            get() = branchDiving.peek()

        companion object _static{
            const val defaultNextStepQuestion = "Какой следующий шаг необходим для решения задачи?"
        }

        override fun process(node: BranchResultNode): QuestionState {
            val nodeVal = node.value == BooleanLiteral(true)
            val currentBranch = currentBranch

            val redir = RedirectQuestionState()
            val skip = object : SkipQuestionState() {
                override fun skip(situation: LearningSituation): QuestionStateChange {
                    val explanation = Explanation("Итак, мы обсудили, почему ${currentBranch.description(situation.templating, nodeVal)}")
                    return QuestionStateChange(explanation, redir)
                }

                override val reachableStates: Collection<QuestionState>
                    get() = listOf(redir)
            }

            nodeStates[node] = skip
            return skip
        }

        override fun process(node: CycleAggregationNode): QuestionState {
            TODO("Not yet implemented")
        }

        override fun process(node: FindActionNode): QuestionState {
            val nextSteps = node.next.keys.map { answer -> answer to nextStep(node, answer)}.toMap()

            val skip = object : SkipQuestionState() {
                override fun skip(situation: LearningSituation): QuestionStateChange {
                    val correctAnswer = node.getAnswer(situation)!!

                    val explanation = Explanation("Мы уже говорили о том, что ${node.next.getFull(correctAnswer)!!.explanation(situation.templating)}")
                    val nextState = nextSteps[correctAnswer]
                    return QuestionStateChange(explanation, nextState)
                }

                override val reachableStates: Collection<QuestionState>
                    get() = nextSteps.values
            }

            nodeStates[node] = skip
            return skip
        }

        override fun process(node: LogicAggregationNode): QuestionState {
            //сначала задать вопрос о результате выполнения узла
            //TODO не задавать, если ветка тривиальна
            val aggregationRedirect = RedirectQuestionState()

            val nextSteps = node.next.map { (outcome) -> outcome to nextStep(node, outcome)}.toMap()
            val mainLinks = node.next.map { (outcome) -> GeneralQuestionState.QuestionStateLink<Pair<Boolean, Boolean>>(
                {situation, answer -> answer.second && (answer.first == outcome) },
                nextSteps[outcome]!!
            )}.plus(GeneralQuestionState.QuestionStateLink<Pair<Boolean, Boolean>>(
                {situation, answer -> !answer.second },
                aggregationRedirect
            )).toSet()

            val resultQuestion = object : CorrectnessCheckQuestionState<Boolean>(mainLinks) {
                override fun text(situation: LearningSituation): String {
                    val descr = node.description(situation.templating, true)
                    return "Верно ли, что $descr?"
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<Pair<Boolean, Boolean>>> {
                    val correctAnswer = node.getAnswer(situation)!!
                    return node.next.info.map { SingleChoiceOption(
                        if(it.key) "Верно" else "Неверно",
                        Explanation("Это не так. Давайте разберемся"),
                        it.key to (it.key == correctAnswer),
                    )}
                }
            }

            //Если результат выполнения узла был выбран неверно, то спросить про результаты веток
            val branchSelectRedirect = RedirectQuestionState()

            val aggregationQuestion = AggregationQuestionState(node, setOf(
                GeneralQuestionState.QuestionStateLink(
                    { situation, answer -> answer && node.getAnswer(situation) == true },
                    nextSteps[true]!!
                ),
                GeneralQuestionState.QuestionStateLink(
                    { situation, answer -> answer && node.getAnswer(situation) == false },
                    nextSteps[false]!!
                ),
                GeneralQuestionState.QuestionStateLink(
                    { situation, answer -> !answer },
                    branchSelectRedirect
                ),
            ))
            aggregationRedirect.redir = aggregationQuestion


            //Если были сделаны ошибки в ветках, то спросить про углубление в одну из веток
            val branchAutomata = node.thoughtBranches.map { it to QuestioningStrategy.defaultFullBranchStrategy.build(it)}.toMap()
            val branchLinks = node.thoughtBranches.map{branch -> GeneralQuestionState.QuestionStateLink<ThoughtBranch>(
                {situation, answer -> answer == branch }, branchAutomata[branch]!!.initState
            )}.toSet()

            val branchSelectQuestion = object : SingleChoiceQuestionState<ThoughtBranch>(branchLinks) {
                override fun text(situation: LearningSituation): String {
                    return "В чем бы вы хотели разобраться подробнее?"
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<ThoughtBranch>> {
                    val options = node.thoughtBranches.filter{branch ->
                        branch.getAnswer(situation) != situation.assumedResult(branch)
                    }.map { branch ->
                        SingleChoiceOption<ThoughtBranch>(
                            "Почему ${branch.description(situation.templating, branch.getAnswer(situation)!!)}?",
                            null,
                            branch
                        )
                    }
                    return options
                }
            }
            branchSelectRedirect.redir = branchSelectQuestion


            //После выхода из веток пропускающее состояние определяет, к какому из верных ответов надо совершить переход
            val shadowSkip = object : SkipQuestionState(){
                override fun skip(situation: LearningSituation): QuestionStateChange {
                    return QuestionStateChange(null, nextSteps[node.getAnswer(situation)!!])
                }

                override val reachableStates: Collection<QuestionState>
                    get() = nextSteps.values
            }
            branchAutomata.values.forEach{it.finalize(shadowSkip)}

            nodeStates[node] = resultQuestion
            return resultQuestion
        }

        override fun process(node: PredeterminingFactorsNode): QuestionState {
            //сначала задать вопрос о результате выполнения узла
            val branchSelectRedirect = RedirectQuestionState()

            val nextSteps = node.next.map { (outcome) -> outcome to nextStep(node, outcome)}.toMap()
            val mainLinks = node.next.map { (outcome) -> GeneralQuestionState.QuestionStateLink<Pair<String, Boolean>>(
                {situation, answer -> answer.second && answer.first == outcome },
                nextSteps[outcome]!!
            )}.plus(GeneralQuestionState.QuestionStateLink<Pair<String, Boolean>>(
                {situation, answer -> !answer.second },
                branchSelectRedirect
            )).toSet()

            val mainQuestion = object : CorrectnessCheckQuestionState<String>(mainLinks) {
                override fun text(situation: LearningSituation): String {
                    return node.question(situation.templating)
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<Pair<String, Boolean>>> {
                    val answer = node.getAnswer(situation)!!
                    val correctOutcome = node.next.info.first { it.key == answer } //TODO возможно стоит изменить систему Outcomes чтобы вот такие конструкции были проще
                    return node.next.info.map { SingleChoiceOption(
                        it.text(situation.templating)!!,
                        Explanation("Это неверно, поскольку ${it.explanation(situation.templating, false)}. " +
                                "В этой ситуации ${correctOutcome.explanation(situation.templating, true)}"),
                        it.key to (it == correctOutcome),
                    )}
                }
            }

            //Если результат выполнения узла был выбран неверно, то спросить про углубление в одну из веток
            val shadowRedirect = RedirectQuestionState()
            val branches = node.next.info.map {it.decidingBranch}.filterNotNull()
            val branchAutomata = branches.map { it to QuestioningStrategy.defaultFullBranchStrategy.build(it)}.toMap()
            val branchLinks = branches.map{branch -> GeneralQuestionState.QuestionStateLink<ThoughtBranch?>(
                {situation, answer -> answer == branch }, branchAutomata[branch]!!.initState
            )}.plus(GeneralQuestionState.QuestionStateLink<ThoughtBranch?>(
                {situation, answer -> answer == null }, shadowRedirect
            )).toSet()

            val branchSelectQuestion = object : SingleChoiceQuestionState<ThoughtBranch?>(branchLinks) {
                override fun text(situation: LearningSituation): String {
                    return "В чем бы вы хотели разобраться подробнее?"
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<ThoughtBranch?>> {
                    val correctAnswer = node.getAnswer(situation)!!
                    val correctBranch = node.next.predeterminingBranch(correctAnswer)

                    val chosenAnswer = mainQuestion.previouslyChosenAnswer(situation)!!.first
                    val incorrectBranch = node.next.predeterminingBranch(chosenAnswer)

                    val options = mutableListOf<SingleChoiceOption<ThoughtBranch?>>()
                    if(incorrectBranch != null && !incorrectBranch.isTrivial())
                        options.add(SingleChoiceOption(
                            "Почему ${incorrectBranch.description(situation.templating, false)}?",
                            null,
                            incorrectBranch,
                        ))
                    if(correctBranch != null && !correctBranch.isTrivial())
                        options.add(SingleChoiceOption(
                            "Почему ${correctBranch.description(situation.templating, true)}?",
                            null,
                            correctBranch,
                        ))
                    if(options.isEmpty())
                        options.add(SingleChoiceOption(
                            "Подробный разбор не нужен",
                            null,
                            null,
                        ))
                    return options
                }
            }
            branchSelectRedirect.redir = branchSelectQuestion


            //После выхода из веток пропускающее состояние определяет, к какому из верных ответов надо совершить переход
            val shadowSkip = object : SkipQuestionState(){
                override fun skip(situation: LearningSituation): QuestionStateChange {
                    return QuestionStateChange(null, nextSteps[node.getAnswer(situation)!!])
                }

                override val reachableStates: Collection<QuestionState>
                    get() = nextSteps.values
            }
            branchAutomata.values.forEach{it.finalize(shadowSkip)}
            shadowRedirect.redir = shadowSkip

            nodeStates[node] = mainQuestion
            return mainQuestion
        }

        override fun process(node: QuestionNode): QuestionState {
            val links = node.next.keys.map { outcomeLiteral ->
                GeneralQuestionState.QuestionStateLink<Pair<Literal, Boolean>>(
                    { situation, answer -> node.getAnswer(situation) == outcomeLiteral },
                    nextStep(node, outcomeLiteral)
                )
            }.toSet()

            val question = object : CorrectnessCheckQuestionState<Literal>(links) {
                override fun text(situation: LearningSituation): String {
                    return node.question(situation.templating)
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<Pair<Literal, Boolean>>> {
                    val answer = node.getAnswer(situation)!!
                    val correctOutcome = node.next.info.first { it.key == answer } //TODO возможно стоит изменить систему Outcomes чтобы вот такие конструкции были проще
                    val explText = correctOutcome.explanation(situation.templating)
                    return node.next.info.map { SingleChoiceOption(
                        it.text(situation.templating)?:it.key.toAnswerString(situation),
                        if(explText != null) Explanation(explText) else null,
                        it.key to (it == correctOutcome),
                    )}
                }
            }

            nodeStates[node] = question
            return question
        }

        override fun process(node: StartNode): QuestionState {
            return node.main.use(this)
        }

        override fun process(branch: ThoughtBranch): QuestionState {
            branchDiving.push(branch)
            val nextState = branch.start.use(this)

            val question = object : CorrectnessCheckQuestionState<DecisionTreeNode>(setOf(
                QuestionStateLink({situation, answer ->  true}, nextState)
            )) {
                override fun text(situation: LearningSituation): String {
                    return branch.nextStepQuestion(situation.templating) ?: "С чего надо начать, чтобы проверить, что ${branch.description(situation.templating, true)}?"
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<Pair<DecisionTreeNode, Boolean>>> {
                    val jumps = branch.getPossibleJumps(situation)

                    return jumps.filter { it !is BranchResultNode}.map{
                        SingleChoiceOption(
                            it.asNextStep(situation.templating),
                            Explanation(branch.nextStepExplanation(situation.templating)?:""),
                            it to (it == branch.start)
                        )
                    }
                }
            }

            branchDiving.pop()
            preNodeStates[branch.start] = question
            return question
        }

        override fun process(node: UndeterminedResultNode): QuestionState {
            TODO("Not yet implemented")
        }

        fun <AnswerType : Any> nextStep(node: LinkNode<AnswerType>, answer: AnswerType): QuestionState {
            val outcome = node.next.info.first { it.key == answer } //TODO возможно стоит изменить систему Outcomes чтобы вот такие конструкции были проще
            val nextNode = outcome.value
            val nextNodeIsResultTrue = nextNode is BranchResultNode && nextNode.value == BooleanLiteral(true)
            val nextNodeIsResultFalse = nextNode is BranchResultNode && nextNode.value == BooleanLiteral(false)
            val nextState = nextNode.use(this)
            val currentBranch = currentBranch

            val question = object : CorrectnessCheckQuestionState<DecisionTreeNode>(setOf(
                QuestionStateLink({situation, answer ->  true}, nextState)
            )) {
                override fun text(situation: LearningSituation): String {
                    return outcome.nextStepQuestion(situation.templating) ?: defaultNextStepQuestion
                }

                override fun options(situation: LearningSituation): List<SingleChoiceOption<Pair<DecisionTreeNode, Boolean>>> {
                    val explanation = outcome.nextStepExplanation(situation.templating)?:""
                    val jumps = node.getPossibleJumps(situation) //TODO? правильная работа со структурой дерева, включая известность переменных

                    val options = jumps.filter { it !is BranchResultNode}.map{SingleChoiceOption(
                        it.asNextStep(situation.templating),
                        Explanation(explanation),
                        it to (it == nextNode),
                    )}.plus(SingleChoiceOption<Pair<DecisionTreeNode, Boolean>>(
                        outcome.nextStepBranchResult(situation.templating, true) ?:"Можно заключить, что ${currentBranch.description(situation.templating, true)}",
                        Explanation(explanation),
                        BranchResultNode(BooleanLiteral(true)) to nextNodeIsResultTrue,
                    )).plus(SingleChoiceOption<Pair<DecisionTreeNode, Boolean>>(
                        outcome.nextStepBranchResult(situation.templating, false) ?:"Можно заключить, что ${currentBranch.description(situation.templating, false)}",
                        Explanation(explanation),
                        BranchResultNode(BooleanLiteral(false)) to nextNodeIsResultFalse,
                    ))
                    return options
                }
            }

            preNodeStates[nextNode] = question
            return question
        }
    }
}