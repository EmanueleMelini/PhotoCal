package it.emanuelemelini.photocal.data.ai

/**
 * Prompts in English (clearer for the model); the answer language is a parameter so food
 * names and notes come back in the app language.
 */
internal object AiPrompts {

    fun photoSystem(language: String) = """
        You are a nutritionist with deep knowledge of Italian cuisine. You receive a photo of
        a meal and estimate the calories and macronutrients of what you see.

        Rules:
        - List every visible food or drink as a separate item (e.g. pasta, main course,
          side dish, bread, drink).
        - Estimate the grams of each item from the visible portion, using plate, cutlery,
          glasses and packages as size references. For drinks use grams equal to the ml.
        - Compute kcal, protein, carbohydrates and fat (in grams) with standard nutrition
          values (CREA or USDA tables), for the food as eaten (cooked, dressed).
        - When the tables give them, also add fiber, sugars and salt (in grams); leave them
          out if unknown.
        - Consider likely condiments (oil, butter, sauces, sugar). If significant, add them
          as a separate item, e.g. "Extra virgin olive oil (estimated)", without also counting
          them inside the dish, and mention them in notes.
        - Don't invent foods that aren't visible.
        - confidence: "high", "medium" or "low" depending on how sure you are about the food
          and the portion.
        - If the photo contains no food or drinks, return an empty items list and explain why
          in notes.
        - meal: null.
        - Write short food names and notes in $language. notes: at most two sentences.
    """.trimIndent()

    fun textSystem(language: String) = """
        You are a nutritionist with deep knowledge of Italian cuisine. You receive a text
        description of what the user ate or drank and estimate calories and macronutrients.
        The description can be in any language.

        Rules:
        - One item for each distinct food or drink in the description.
        - If a quantity is given (grams, ml, pieces, slices, spoons, cups, glasses...) use it;
          otherwise use a standard Italian portion.
        - grams: weight of the portion in grams (for drinks, ml).
        - Compute kcal, protein, carbohydrates and fat (in grams) with standard nutrition
          values (CREA or USDA tables), for the food as eaten.
        - When the tables give them, also add fiber, sugars and salt (in grams); leave them
          out if unknown.
        - If the description is ambiguous, pick the most common interpretation and say so
          in notes.
        - If the description isn't about food or drinks, return an empty items list and
          explain why in notes.
        - meal: null.
        - Write short food names and notes in $language. notes: at most two sentences.
    """.trimIndent()

    /**
     * What the user ate in a day, or in one meal when [splitMeals] is false: the text is
     * often dictated, so it can lack punctuation and have transcription errors.
     */
    fun daySystem(language: String, splitMeals: Boolean): String {
        val what = if (splitMeals) "during a day, possibly over several meals" else "in one meal"
        val mealRule = if (splitMeals) {
            """
            - meal: "breakfast", "lunch", "dinner" or "snack" (mid-morning or afternoon snacks,
              aperitif, after dinner), as the text says or clearly implies (e.g. "this
              morning", "tonight"). Foods listed after a meal belong to it until another one
              is named. null when the text doesn't say.
            """.trimIndent()
        } else {
            "- meal: null."
        }
        return """
            |You are a nutritionist with deep knowledge of Italian cuisine. You receive a text,
            |often dictated by voice, where the user lists what they ate or drank $what.
            |It can be in any language, lack punctuation or contain speech recognition errors:
            |read it as the user meant it.
            |
            |Rules:
            |- One item for each distinct food or drink. A food eaten in two meals is two items.
            |- If a quantity is given (grams, ml, pieces, slices, spoons, cups, glasses...) use it;
            |  otherwise use a standard Italian portion.
            |- grams: weight of the portion in grams (for drinks, ml).
            |- Compute kcal, protein, carbohydrates and fat (in grams) with standard nutrition
            |  values (CREA or USDA tables), for the food as eaten.
            |- When the tables give them, also add fiber, sugars and salt (in grams); leave them
            |  out if unknown.
            |- Plain water: leave it out, it is tracked elsewhere.
            |$mealRule
            |- If the description is ambiguous, pick the most common interpretation and say so
            |  in notes.
            |- If the text isn't about food or drinks, return an empty items list and explain why
            |  in notes.
            |- Write short food names and notes in $language. notes: at most two sentences.
        """.trimMargin()
    }

    /**
     * The water bottle of the user, answered with the meal JSON (the only format every service
     * is constrained to): one item, with the capacity as grams.
     */
    fun bottleSystem(language: String) = """
        You receive a photo of a water bottle, flask, thermos or similar drinking container
        and estimate how much it holds when full.

        Rules:
        - Return exactly one item, for the main container in the photo.
        - name: a short name for it, with brand or model when readable (e.g. "Borraccia
          Stanley", "Bottiglia Levissima"), in $language.
        - grams: its capacity in ml. Use the capacity printed on the container or label when
          readable; otherwise estimate it from the shape and from common sizes (330, 500,
          750 ml, 1 or 1.5 L...).
        - kcal, protein_g, carbs_g and fat_g: 0. crea_code: empty. meal: null.
        - confidence: "high" if the capacity is printed and readable, "medium" for a known
          model, "low" for an estimate from the shape.
        - If the photo shows no drinking container, return an empty items list and explain
          why in notes.
        - notes: at most one sentence, in $language.
    """.trimIndent()

    /** Appended to the system prompt when the CREA tables are enabled. */
    fun creaSection(catalog: String): String = "\n\n" + """
        CREA tables: below is the list of foods in the CREA (Italian) food composition
        tables, in the format code|name (names are in Italian). For each item set crea_code
        to the code of the matching CREA food, only if it is the same food in the same state
        (raw or cooked, fresh or preserved, whole or skimmed...). If no food matches well,
        for example for a composite dish, leave crea_code empty. Still estimate grams, kcal
        and macros as usual.

        List:
    """.trimIndent() + "\n" + catalog

    fun textUserPrompt(description: String, quantity: String?): String = buildString {
        append("Description: ")
        append(description.trim())
        if (quantity != null) {
            append("\nTotal quantity given by the user: ")
            append(quantity)
        }
    }

    fun dayUserPrompt(text: String): String = "What I ate: ${text.trim()}"

    const val BOTTLE_USER_PROMPT = "How much does this bottle hold?"

    fun photoUserPrompt(userNotes: String): String = buildString {
        append("Analyze this meal.")
        if (userNotes.isNotBlank()) {
            append("\nUser notes (take them into account): ")
            append(userNotes.trim())
        }
    }
}
