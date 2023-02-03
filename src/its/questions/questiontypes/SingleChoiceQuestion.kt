package its.questions.questiontypes

//TODO сделать большой рефакторинг
class SingleChoiceQuestion(val shouldBeFinal : Boolean, val text: String, val options : List<AnswerOption>){
    init{
        require(options.count { it.isTrue } == 1)
    }
    fun ask(): AnswerStatus{
        return askWithInfo().first
    }

    fun askWithInfo(): Pair<AnswerStatus, Any?> {
        if(options.size == 1)
            return AnswerStatus.CORRECT to options.first().assocValue
        println()
        println(text)
        println("(Вопрос с единственным вариантом ответа)")
        val shuffle = options.shuffled()
        shuffle.forEachIndexed {i, option -> println(" ○ ${i+1}. ${option.text}") }

        var answers = getAnswers()
        while(answers.size != 1 || answers.any { it-1 !in shuffle.indices}){
            println("Неверный формат ввода для вопроса с единственным вариантом ответа.")
            answers = getAnswers()
        }
        val answer = answers.single()

        return if(shuffle[answer-1].isTrue){
            AnswerStatus.CORRECT
        } else{
            if(shuffle[answer-1].explanation.isNullOrBlank())
                println("Это неверно. В данном случае правильным ответом является '${options.first { it.isTrue }.text}'.")
            else
                println(shuffle[answer-1].explanation)

            if(!shouldBeFinal)
                AnswerStatus.INCORRECT_CONTINUE
            else if(getExplanationHelped())
                AnswerStatus.INCORRECT_EXPLAINED
            else
                AnswerStatus.INCORRECT_STUCK
        } to shuffle[answer-1].assocValue
    }
}