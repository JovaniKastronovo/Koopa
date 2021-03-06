package koopa.tokenizers.cobol.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import junit.framework.Assert;
import junit.framework.TestCase;

import koopa.tokenizers.Tokenizer;
import koopa.tokenizers.cobol.CompilerDirectivesTokenizer;
import koopa.tokenizers.cobol.ContinuationWeldingTokenizer;
import koopa.tokenizers.cobol.LineContinuationTokenizer;
import koopa.tokenizers.cobol.LineSplittingTokenizer;
import koopa.tokenizers.cobol.ProgramAreaTokenizer;
import koopa.tokenizers.cobol.PseudoLiteralTokenizer;
import koopa.tokenizers.cobol.SeparatorTokenizer;
import koopa.tokenizers.cobol.SourceFormat;
import koopa.tokenizers.cobol.SourceFormattingDirectivesFilter;
import koopa.tokenizers.cobol.TokenCountVerifiyingTokenizer;
import koopa.tokenizers.cobol.TokenStateVerifiyingTokenizer;
import koopa.tokenizers.cobol.tags.AreaTag;
import koopa.tokenizers.cobol.tags.ContinuationsTag;
import koopa.tokenizers.cobol.tags.SyntacticTag;
import koopa.tokenizers.generic.FilteringTokenizer;
import koopa.tokens.Token;
import koopa.tokens.TokenFilter;

public class TokenizerTest extends TestCase {

	@Test
	public void test() throws IOException {
		final List<Token> fixedTokens = process("testsuite/koopa/test.CBL",
				SourceFormat.FIXED);
		final List<Token> freeTokens = process("testsuite/koopa/free.CBL",
				SourceFormat.FREE);

		boolean mismatched = false;
		final int numberOfFixedTokens = fixedTokens.size();
		final int numberOfFreeTokens = freeTokens.size();
		final int numberOfTokens = Math.min(numberOfFixedTokens,
				numberOfFreeTokens);

		for (int i = 0; !mismatched && i < numberOfTokens; i++) {
			final Token fixedToken = fixedTokens.get(i);
			final Token freeToken = freeTokens.get(i);

			if (!fixedToken.getText().equals(freeToken.getText())) {
				System.out.println("Mismatch starts with " + fixedToken
						+ " vs. " + freeToken + ".");

				mismatched = true;
				break;
			}

			for (Object tag : fixedToken.getTags()) {
				if (tag instanceof ContinuationsTag) {
					continue;
				}

				if (!freeToken.hasTag(tag)) {
					System.out.println(fixedToken + " has tag " + tag
							+ ", but " + freeToken + " does not.");
					mismatched = true;
				}
			}

			for (Object tag : freeToken.getTags()) {
				if (!fixedToken.hasTag(tag)) {
					System.out.println(freeToken + " has tag " + tag + ", but "
							+ fixedToken + " does not.");
					mismatched = true;
				}
			}
		}

		Assert.assertFalse("Inputs should produce the same tokens.", mismatched
				|| numberOfFixedTokens != numberOfFreeTokens);
	}

	public static List<Token> process(String filename, SourceFormat format)
			throws IOException {
		System.out.println("Tokenizing " + filename);

		FileReader reader = new FileReader(filename);

		// We will be building up our tokenizer in several stages. Each stage
		// takes the preceding tokenizer, and extends its abilities.
		Tokenizer tokenizer;

		// The tokenizers in this sequence should generate the expected tokens.

		tokenizer = new LineSplittingTokenizer(new BufferedReader(reader));
		tokenizer = new CompilerDirectivesTokenizer(tokenizer, format);
		tokenizer = new ProgramAreaTokenizer(tokenizer, format);
		tokenizer = new SourceFormattingDirectivesFilter(tokenizer);

		if (format == SourceFormat.FIXED) {
			tokenizer = new LineContinuationTokenizer(tokenizer);
			tokenizer = new ContinuationWeldingTokenizer(tokenizer);
		}

		tokenizer = new SeparatorTokenizer(tokenizer);
		tokenizer = new PseudoLiteralTokenizer(tokenizer);

		// This tokenizer partly tests that assumption by comparing the number
		// of tokens in the program text area on each line with the expected
		// number. The number of expected tokens is encoded in the indicator
		// area of the test file. If it is missing (which is allowed) the line
		// is not tested in this way.
		TokenCountVerifiyingTokenizer countVerifier = null;
		tokenizer = countVerifier = new TokenCountVerifiyingTokenizer(tokenizer);

		// This tokenizer tests the tagging of tokens, making sure that they are
		// in a consistent state.
		TokenStateVerifiyingTokenizer stateVerifier = null;
		tokenizer = stateVerifier = new TokenStateVerifiyingTokenizer(tokenizer);

		// Here we filter out all tokens which are not part of the program text
		// area (comments are not considered part of this area). This leaves us
		// with the pure code, which should be perfect for processing by a
		// parser.
		tokenizer = new FilteringTokenizer(tokenizer, AreaTag.PROGRAM_TEXT_AREA);

		// Here we filter out all pure whitespace separators. This leaves us
		// with only the "structural" tokens which are of interest to a parser.
		tokenizer = new FilteringTokenizer(tokenizer, new TokenFilter() {
			public boolean accepts(Token token) {
				return !token.hasTag(SyntacticTag.SEPARATOR)
						|| !token.getText().trim().equals("");
			}
		});

		// The following is handy in debugging:
		// tokenizer = new PrintingTokenizer(tokenizer, System.out);

		// So far, no tokenization will have occurred. The file will only start
		// getting tokenized when we start asking for tokens. We do this in the
		// following loop, which also counts the number of tokens which are
		// returned at the end.
		List<Token> tokens = new ArrayList<Token>();
		Token nextToken = null;
		while ((nextToken = tokenizer.nextToken()) != null) {
			tokens.add(nextToken);
			// System.out.println(nextToken);
		}

		// Some of our tokenizers may be threaded. We need to make sure that any
		// threads they hold get stopped. This is what we do here. The message
		// will get passed along the chain of tokenizers, giving each a chance
		// to stop running.
		tokenizer.quit();

		// Finally, some reporting on the results of the tokenizing.
		System.out.println("Processed " + tokens.size()
				+ " top level token(s).");

		if (countVerifier != null) {
			for (String message : countVerifier.getErrorMessages()) {
				System.out.println(message);
			}
		}

		if (stateVerifier != null) {
			for (String message : stateVerifier.getErrorMessages()) {
				System.out.println(message);
			}
		}

		System.out.println();

		Assert.assertTrue("Counts as defined in source should match.",
				countVerifier.getErrorMessages().size() == 0);

		Assert.assertTrue("Token states should be as expected.", countVerifier
				.getErrorMessages().size() == 0);

		return tokens;
	}
}
