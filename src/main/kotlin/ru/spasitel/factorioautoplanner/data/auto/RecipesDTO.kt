class RecipesDTO : ArrayList<RecipesDTOItem>()

data class RecipesDTOItem(
    val energy_required: Double,
    val ingredients: List<Ingredient>,
    val name: String,
    val result: String,
    val result_count: Int,
    val results: List<Result>
)

data class Ingredient(
    val amount: Int,
    val name: String
)

data class Result(
    val amount: Int,
    val name: String
)