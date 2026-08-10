package app.pinimage.edit

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Basic editor activity (crop/rotate/flip). Implemented in a later commit.
 */
class EditActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_RESULT_URI = "extra_result_uri"
        const val REQUEST_EDIT = 2001
    }
}
