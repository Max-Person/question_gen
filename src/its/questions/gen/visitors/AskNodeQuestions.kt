package its.questions.gen.visitors

import its.model.nodes.*
import its.model.nodes.visitors.DecisionTreeBehaviour
import its.questions.gen.QuestionGenerator
import its.questions.gen.TemplatingUtils._static.replaceAlternatives
import its.questions.questiontypes.*

class AskNodeQuestions(val q : QuestionGenerator) : DecisionTreeBehaviour<AnswerStatus> {
    override fun process(node: BranchResultNode): AnswerStatus {
        return AnswerStatus.CORRECT
    }

    override fun process(node: CycleAggregationNode): AnswerStatus {
        TODO("Not yet implemented")
    }

    override fun process(node: FindActionNode): AnswerStatus {
        //TODO разобраться как задавать вопросы на развилки в действиях
        return AnswerStatus.CORRECT
    }

    override fun process(node: LogicAggregationNode): AnswerStatus {
        val answer = q.answers[node.additionalInfo[ALIAS_ATR]].toBoolean()
        val descr = q.templating.process(node.additionalInfo["description"]!!.replaceAlternatives(true))
        val q1 = SingleChoiceQuestion(
            false,
            "Верно ли, что $descr?",
            listOf(
                AnswerOption("Верно", answer, "Это неверно." ),
                AnswerOption("Неверно", !answer, "Это неверно." ),)
        )
        if(q1.ask() == AnswerStatus.CORRECT)
            return AnswerStatus.CORRECT

        val descrIncorrect = q.templating.process(node.additionalInfo["description"]!!.replaceAlternatives(!answer))
        val q2 = AggregationQuestion(
            "Почему вы считаете, что $descrIncorrect?",
            node.logicalOp,
            !answer,
            node.thoughtBranches.map {
                val branchAnswer = q.answers[it.additionalInfo[ALIAS_ATR]].toBoolean()
                AggregationQuestion.AnswerOption(
                    it,
                    q.templating.process(it.additionalInfo["description"]!!.replaceAlternatives(true)),
                    branchAnswer,
                    q.templating.process(it.additionalInfo["description"]!!.replaceAlternatives(branchAnswer)),
                    )
            }
        )

        val incorrect = q2.ask()
        if(incorrect.isEmpty())
            return AnswerStatus.CORRECT

        val branch = Prompt(
            "Хотите ли вы разобраться подробнее?",
            incorrect.map {
                val branchAnswer = q.answers[it.additionalInfo[ALIAS_ATR]].toBoolean()
                "Почему " + q.templating.process(it.additionalInfo["description"]!!.replaceAlternatives(branchAnswer)) + "?" to it }
            ).ask()

        return q.process(branch, !q.answers[branch.additionalInfo[ALIAS_ATR]].toBoolean())
    }

    override fun process(node: PredeterminingFactorsNode): AnswerStatus {
        TODO("Not yet implemented")
    }

    override fun process(node: QuestionNode): AnswerStatus {
        TODO("Not yet implemented")
    }

    override fun process(node: StartNode): AnswerStatus {
        TODO("Not yet implemented")
    }

    override fun process(branch: ThoughtBranch): AnswerStatus {
        TODO("Not yet implemented")
    }

    override fun process(node: UndeterminedResultNode): AnswerStatus {
        TODO("Not yet implemented")
    }
}