package it.emanuelemelini.photocal.data.nutrition

/** The user's water bottle; [name] can be empty. */
data class Bottle(val name: String, val ml: Int) {
    companion object {
        /** From a small bottle to a large thermos. */
        val ML_RANGE = 100..3_000
    }
}
