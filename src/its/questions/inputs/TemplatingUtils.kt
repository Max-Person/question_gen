package its.questions.inputs

import com.github.drapostolos.typeparser.ParserHelper
import com.github.drapostolos.typeparser.TypeParser
import com.github.max_person.templating.InterpretationData
import com.github.max_person.templating.TemplatingSafeMethod
import its.model.nodes.*
import padeg.lib.Padeg

class TemplatingUtils(val situation : LearningSituation) {
    enum class Case{
        Nom, //именительный (кто? что?)
        Gen, //родительный (кого? чего?)
        Dat, //дательный (кому? чему?)
        Acc, //винительный (кого? что?)
        Ins, //творительный (кем? чем?)
        Pre, //предложный (о ком? о чем?)
        ;

        companion object _static{
            @JvmStatic
            fun fromString(str: String) : Case? {
                return when(str.lowercase()){
                    "и.п.", "им.п.", "и", "им", "и.", "им.", "n", "nom", "nom." -> Nom
                    "р.п.", "род.п.", "р", "род", "р.", "род.", "g", "gen", "gen." -> Gen
                    "д.п.", "дат.п.", "д", "дат", "д.", "дат.", "d", "dat", "dat." -> Dat
                    "в.п.", "вин.п.", "в", "вин", "в.", "вин.", "a", "acc", "acc." -> Acc
                    "т.п.", "тв.п.", "т", "тв", "т.", "тв.", "i", "ins", "ins." -> Ins
                    "п.п.", "пр.п.", "п", "пр", "п.", "пр.", "p", "pre", "pre." -> Pre
                    else -> null
                }
            }
        }
    }

    companion object _static{
        @JvmStatic
        internal val templatingParser  = TypeParser.newBuilder()
            .registerParser(Case::class.java) { s: String, h: ParserHelper -> Case.fromString(s) }
            .build()

        @JvmStatic
        fun String.toCase(case: Case?) : String{
            return Padeg.getAppointmentPadeg(this, (case?: Case.Nom).ordinal+1).replace(Regex("\\s+"), " ")
        }

        @JvmStatic
        fun String.capitalize() : String{
            return this.replaceFirstChar { it.uppercaseChar() }
        }


        @JvmStatic
        private fun String.cleanup() : String{
            return this.replace(Regex("\\s+"), " ")
        }

        @JvmStatic
        internal fun String.interpret(interpretationData: InterpretationData) : String{
            return interpretationData.interpret(this).cleanup()
        }

        //region Получение и шаблонизация текстовой информации

        //Узлы
        @JvmStatic
        internal fun DecisionTreeNode.asNextStep(localizationCode: String, interpretationData: InterpretationData) : String {
            return additionalInfo["${localizationCode}_asNextStep"]!!.interpret(interpretationData)
        }

        @JvmStatic
        internal fun DecisionTreeNode.question(localizationCode: String, interpretationData: InterpretationData) : String {
            return additionalInfo["${localizationCode}_question"]!!.interpret(interpretationData) //TODO Если такого нет - у агрегаций, например.
        }

        @JvmStatic
        internal fun DecisionTreeNode.endingCause(localizationCode: String, interpretationData: InterpretationData) : String {
            return additionalInfo["${localizationCode}_endingCause"]!!.interpret(interpretationData) //TODO Если такого нет - т.е. у не-конечных узлов
        }

        @JvmStatic
        internal fun LogicAggregationNode.description(localizationCode: String, interpretationData: InterpretationData, result : Boolean) : String {
            return additionalInfo["${localizationCode}_description"]!!.interpret(interpretationData.usingVar("result", result))
        }

        //Выходы (стрелки)
        @JvmStatic
        internal fun Outcome<*>.text(localizationCode: String, interpretationData: InterpretationData) : String? {
            return additionalInfo["${localizationCode}_text"]?.interpret(interpretationData)
        }

        @JvmStatic
        internal fun Outcome<*>.explanation(localizationCode: String, interpretationData: InterpretationData) : String? { //TODO? если это PredeterminingOutcome то использовать другую функцию
            return additionalInfo["${localizationCode}_explanation"]?.interpret(interpretationData)
        }

        @JvmStatic
        internal fun FindActionNode.FindErrorCategory.explanation(localizationCode: String, interpretationData: InterpretationData, entityAlias : String) : String {
            return additionalInfo["${localizationCode}_explanation"]!!.interpret(interpretationData.usingVar("checked", entityAlias))
        }

        @JvmStatic
        internal fun PredeterminingOutcome.explanation(localizationCode: String, interpretationData: InterpretationData, result: Boolean) : String {
            return additionalInfo["${localizationCode}_explanation"]!!.interpret(interpretationData.usingVar("result", result))
        }

        @JvmStatic
        internal fun Outcome<*>.nextStepQuestion(localizationCode: String, interpretationData: InterpretationData) : String? {
            return additionalInfo["${localizationCode}_nextStepQuestion"]?.interpret(interpretationData)
        }

        @JvmStatic
        internal fun Outcome<*>.nextStepBranchResult(localizationCode: String, interpretationData: InterpretationData, branchResult : Boolean) : String? {
            return additionalInfo["${localizationCode}_nextStepBranchResult"]?.interpret(interpretationData.usingVar("branchResult", branchResult))
        }

        @JvmStatic
        internal fun Outcome<*>.nextStepExplanation(localizationCode: String, interpretationData: InterpretationData) : String? {
            return additionalInfo["${localizationCode}_nextStepExplanation"]?.interpret(interpretationData)
        }

        //Ветки
        @JvmStatic
        internal fun ThoughtBranch.description(localizationCode: String, interpretationData: InterpretationData, result : Boolean) : String {
            return additionalInfo["${localizationCode}_description"]!!.interpret(interpretationData.usingVar("result", result))
        }

        @JvmStatic
        internal fun ThoughtBranch.nextStepQuestion(localizationCode: String, interpretationData: InterpretationData) : String? {
            return additionalInfo["${localizationCode}_nextStepQuestion"]?.interpret(interpretationData)
        }

        @JvmStatic
        internal fun ThoughtBranch.nextStepBranchResult(localizationCode: String, interpretationData: InterpretationData, branchResult : Boolean) : String? {
            return additionalInfo["${localizationCode}_nextStepBranchResult"]?.interpret(interpretationData.usingVar("branchResult", branchResult))
        }

        @JvmStatic
        internal fun ThoughtBranch.nextStepExplanation(localizationCode: String, interpretationData: InterpretationData) : String? {
            return additionalInfo["${localizationCode}_nextStepExplanation"]?.interpret(interpretationData)
        }

        //endregion
    }

    @TemplatingSafeMethod("val")
    fun getVariableValue(varName: String, case: Case) : String{
        return situation.entityDictionary.getByVariable(varName)!!.specificName.toCase(case)
    }

    @TemplatingSafeMethod("obj")
    fun getEntity(alias: String, case: Case) : String{
        return situation.entityDictionary.get(alias)!!.specificName.toCase(case)
    }

    @TemplatingSafeMethod("class")
    fun getVariableClassname(varName: String, case: Case) : String{
        return with(situation.domainLocalization){
            situation.entityDictionary.getByVariable(varName)!!.clazz.localizedName.toCase(case)
        }
    }
}