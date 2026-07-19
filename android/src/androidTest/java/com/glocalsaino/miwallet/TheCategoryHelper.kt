package com.glocalsaino.miwallet

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import com.glocalsaino.miwallet.functions.getHumanCategoryString
import com.glocalsaino.miwallet.model.PassDefinitions

class TheCategoryHelper {

    @Test
    fun testAllCategoriesAreTranslated() {

        val allTranslationSet = PassDefinitions.TYPE_TO_NAME.keys
                .map(::getHumanCategoryString)
                .toSet()

        assertThat(allTranslationSet.size).isEqualTo(PassDefinitions.TYPE_TO_NAME.keys.size)
    }

}
