import React from 'react';

/**
 * Keyword highlighting aligned with ImageLog rules: plain `<mark>`, `mark.encrypted-highlight` for
 * quoted JSON string values `"[...]"`, OR across keywords, heuristics when backend flags are absent.
 *
 * @param {string} text - Display text (may differ from original in edge cases)
 * @param {string[]} keywords - Search keywords
 * @param {string|null} [originalText] - Source for pattern detection; defaults to `text`
 * @param {boolean} [hasEncryptedMatch=false] - Backend hint that match involved encrypted payload
 * @param {string|null} [fieldKeyword] - Additional term (field-scoped search)
 * @param {boolean} [keywordBackedFieldHighlight=false]
 * @returns {string} HTML string (not sanitized — same contract as ImageLog callers)
 */
export function highlightKeywordsAsHtml(
  text,
  keywords,
  originalText = null,
  hasEncryptedMatch = false,
  fieldKeyword = null,
  keywordBackedFieldHighlight = false
) {
  if (!text && !originalText) {
    return text || '';
  }

  const allKeywords = [];
  if (keywords && Array.isArray(keywords) && keywords.length > 0) {
    allKeywords.push(...keywords);
  }
  if (fieldKeyword && typeof fieldKeyword === 'string' && fieldKeyword.trim() !== '') {
    allKeywords.push(fieldKeyword.trim());
  }

  if (allKeywords.length === 0) {
    return text || originalText || '';
  }

  const sourceText = originalText || text;
  if (!sourceText) {
    return text || '';
  }

  let highlightedText = String(sourceText);

  const quotedBracketPattern = /"(\[[^\]]*\])"/g;
  const encryptedMatches = [];
  const tempSource = String(sourceText);
  let match;
  quotedBracketPattern.lastIndex = 0;
  while ((match = quotedBracketPattern.exec(tempSource)) !== null) {
    const fullMatch = match[1];
    encryptedMatches.push({
      fullMatch,
      encryptedContent: fullMatch.slice(1, -1),
      index: match.index + 1,
      length: fullMatch.length,
    });
  }

  allKeywords.forEach((keyword) => {
    if (!keyword || typeof keyword !== 'string' || keyword.trim() === '') return;
    const trimmedKeyword = keyword.trim();
    const escapedKeyword = trimmedKeyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

    const keywordInsideAnyBracket = encryptedMatches.some((em) =>
      em.fullMatch.toLowerCase().includes(trimmedKeyword.toLowerCase())
    );

    if (
      encryptedMatches.length > 0 &&
      (hasEncryptedMatch ||
        fieldKeyword ||
        keywordInsideAnyBracket ||
        keywordBackedFieldHighlight)
    ) {
      encryptedMatches.forEach((encryptedMatch) => {
        const encryptedValue = encryptedMatch.fullMatch;
        if (!highlightedText.includes(`<mark class="encrypted-highlight">${encryptedValue}</mark>`)) {
          const escapedEncrypted = encryptedValue.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          const regex = new RegExp(escapedEncrypted, 'g');
          highlightedText = highlightedText.replace(regex, (m) => {
            if (m.includes('<mark')) {
              return m;
            }
            return `<mark class="encrypted-highlight">${m}</mark>`;
          });
        }
      });
    }

    const placeholders = [];
    let maskedText = highlightedText;

    encryptedMatches.forEach((encryptedMatch, idx) => {
      const placeholder = `__ENCRYPTED_${idx}__`;
      placeholders.push({
        placeholder,
        value: encryptedMatch.fullMatch,
      });
      maskedText = maskedText.replace(encryptedMatch.fullMatch, placeholder);
    });

    const keywordRegex = new RegExp(`(${escapedKeyword})`, 'gi');
    maskedText = maskedText.replace(keywordRegex, (m, p1) => {
      return `<mark>${p1}</mark>`;
    });

    placeholders.forEach(({ placeholder, value }) => {
      maskedText = maskedText.replace(placeholder, value);
    });

    highlightedText = maskedText;
  });

  return highlightedText;
}

/**
 * True when `highlightKeywordsAsHtml` would add match-indicating markup (`<mark>` or
 * `mark.encrypted-highlight`) for this line — same semantics as the highlighter.
 *
 * @param {string} text - Line text (logical line from payload split by `\n`)
 * @param {string[]} keywords - Search keywords
 * @param {string|null} [originalText]
 * @param {boolean} [hasEncryptedMatch]
 * @param {string|null} [fieldKeyword]
 * @param {boolean} [keywordBackedFieldHighlight]
 * @returns {boolean}
 */
export function lineHasKeywordHighlightHtml(
  text,
  keywords,
  originalText = null,
  hasEncryptedMatch = false,
  fieldKeyword = null,
  keywordBackedFieldHighlight = false
) {
  const html = highlightKeywordsAsHtml(
    text,
    keywords,
    originalText,
    hasEncryptedMatch,
    fieldKeyword,
    keywordBackedFieldHighlight
  );
  return /<mark\b/i.test(html);
}

/**
 * @param {string} text
 * @param {string[]} keywords
 * @param {string|null} [originalText]
 * @param {boolean} [hasEncryptedMatch]
 * @param {string|null} [fieldKeyword]
 * @param {boolean} [keywordBackedFieldHighlight]
 * @returns {React.ReactElement}
 */
export function highlightKeywords(
  text,
  keywords,
  originalText = null,
  hasEncryptedMatch = false,
  fieldKeyword = null,
  keywordBackedFieldHighlight = false
) {
  const highlightedHtml = highlightKeywordsAsHtml(
    text,
    keywords,
    originalText,
    hasEncryptedMatch,
    fieldKeyword,
    keywordBackedFieldHighlight
  );
  return React.createElement('span', { dangerouslySetInnerHTML: { __html: highlightedHtml } });
}
