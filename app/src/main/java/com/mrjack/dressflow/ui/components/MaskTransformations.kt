package com.mrjack.dressflow.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Máscara brasileira de telefone: (XX) XXXXX-XXXX ou (XX) XXXX-XXXX */
class BrPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(11)
        val out = buildString {
            when {
                digits.isEmpty() -> Unit
                digits.length <= 2 -> {
                    append("("); append(digits)
                }
                digits.length <= 6 -> {
                    append("("); append(digits.take(2)); append(") "); append(digits.drop(2))
                }
                digits.length <= 10 -> {
                    append("("); append(digits.take(2)); append(") ")
                    append(digits.drop(2).take(4)); append("-"); append(digits.drop(6))
                }
                else -> {
                    append("("); append(digits.take(2)); append(") ")
                    append(digits.drop(2).take(5)); append("-"); append(digits.drop(7))
                }
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (digits.isEmpty()) return 0
                return when {
                    offset == 0 -> 1       // "(" added before
                    offset <= 2 -> offset + 1   // inside area code
                    offset <= 6 -> offset + 3   // ") " added
                    offset <= 10 -> offset + 4  // "-" added
                    else -> offset + 5
                }.coerceAtMost(out.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 1 -> 0
                    offset <= 3 -> (offset - 1).coerceAtLeast(0)
                    offset <= 9 -> (offset - 3).coerceAtLeast(0)
                    offset <= 15 -> (offset - 4).coerceAtLeast(0)
                    else -> (offset - 5).coerceAtLeast(0)
                }.coerceAtMost(digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

/** Máscara de CPF: XXX.XXX.XXX-XX */
class CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(11)
        val out = buildString {
            when {
                digits.length <= 3 -> append(digits)
                digits.length <= 6 -> {
                    append(digits.take(3)); append("."); append(digits.drop(3))
                }
                digits.length <= 9 -> {
                    append(digits.take(3)); append(".")
                    append(digits.drop(3).take(3)); append("."); append(digits.drop(6))
                }
                else -> {
                    append(digits.take(3)); append(".")
                    append(digits.drop(3).take(3)); append(".")
                    append(digits.drop(6).take(3)); append("-"); append(digits.drop(9))
                }
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 3 -> offset
                offset <= 6 -> offset + 1
                offset <= 9 -> offset + 2
                else        -> offset + 3
            }.coerceAtMost(out.length)
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 3  -> offset
                offset <= 7  -> (offset - 1).coerceAtLeast(0)
                offset <= 11 -> (offset - 2).coerceAtLeast(0)
                else         -> (offset - 3).coerceAtLeast(0)
            }.coerceAtMost(digits.length)
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
