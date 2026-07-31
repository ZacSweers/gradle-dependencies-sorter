package com.squareup.sort

import org.antlr.v4.runtime.ParserRuleContext

internal data class RewrittenBlock(
  val text: String,
  val isAlreadyOrdered: Boolean,
)

internal data class SourceReplacement(
  val startIndex: Int,
  val stopIndex: Int,
  val text: String,
)

/** Returns changed blocks that are not contained by another changed block. */
internal fun Map<ParserRuleContext, RewrittenBlock>.topmostChanges(): List<Map.Entry<ParserRuleContext, RewrittenBlock>> {
  val changed = entries.filter { !it.value.isAlreadyOrdered }
  return changed.filter { candidate ->
    changed.none { other ->
      other.key !== candidate.key && other.key.contains(candidate.key)
    }
  }
}

/** Applies explicit replacements and rewritten child blocks to one source range. */
internal fun Map<ParserRuleContext, RewrittenBlock>.rewriteSource(
  startIndex: Int,
  stopIndex: Int,
  replacements: List<SourceReplacement> = emptyList(),
  originalText: (Int, Int) -> String,
): String {
  val descendants = entries
    .filter { (block, rewritten) ->
      !rewritten.isAlreadyOrdered &&
        block.isWithin(startIndex, stopIndex) &&
        replacements.none { replacement ->
          block.isWithin(replacement.startIndex, replacement.stopIndex)
        }
    }
    .filter { candidate ->
      entries.none { other ->
        !other.value.isAlreadyOrdered &&
          other.key !== candidate.key &&
          other.key.isWithin(startIndex, stopIndex) &&
          other.key.contains(candidate.key)
      }
    }
    .map { (block, rewritten) ->
      SourceReplacement(block.start.startIndex, block.stop.stopIndex, rewritten.text)
    }

  val allReplacements = (replacements + descendants).sortedBy { it.startIndex }

  return buildString {
    var cursor = startIndex
    allReplacements.forEach { replacement ->
      if (cursor < replacement.startIndex) {
        append(originalText(cursor, replacement.startIndex - 1))
      }
      append(replacement.text)
      cursor = replacement.stopIndex + 1
    }
    if (cursor <= stopIndex) {
      append(originalText(cursor, stopIndex))
    }
  }
}

private fun ParserRuleContext.contains(other: ParserRuleContext): Boolean {
  return other.isWithin(start.startIndex, stop.stopIndex)
}

private fun ParserRuleContext.isWithin(startIndex: Int, stopIndex: Int): Boolean {
  return start.startIndex >= startIndex && stop.stopIndex <= stopIndex
}
