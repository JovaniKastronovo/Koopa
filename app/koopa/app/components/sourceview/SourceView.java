package koopa.app.components.sourceview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import koopa.app.listeners.TokenSelectionListener;
import koopa.parsers.ParseResults;
import koopa.parsers.cobol.CobolParser;
import koopa.parsers.cobol.ParsingCoordinator;
import koopa.parsers.cobol.ParsingListener;
import koopa.tokenizers.cobol.TokenTracker;
import koopa.tokenizers.cobol.tags.AreaTag;
import koopa.tokenizers.cobol.tags.SyntacticTag;
import koopa.tokens.Token;

@SuppressWarnings("serial")
public class SourceView extends JPanel implements ParsingListener {

	private JTextPane pane = null;

	private JScrollPane scroll = null;

	// private SourceViewIntermediateTokenizer tokenizer = null;

	private List<Integer> lineOffsets = new ArrayList<Integer>();

	private TokenTracker tokenTracker = null;

	private SourceViewSink sink = null;

	private Token selectedToken = null;

	private List<TokenSelectionListener> tokenSelectionListeners = new ArrayList<TokenSelectionListener>();

	public SourceView(ParsingCoordinator coordinator) {
		setupComponents();

		coordinator.addParsingListener(this);
	}

	private void setupComponents() {
		pane = new JTextPane() {
			public boolean getScrollableTracksViewportWidth() {
				return false;
			}
		};

		pane.setEditable(false);
		pane.getCaret().setVisible(true);
		pane.setMargin(new Insets(0, 5, 0, 0));

		pane.addCaretListener(new CaretListener() {
			public void caretUpdate(CaretEvent e) {
				final Caret caret = pane.getCaret();
				if (!caret.isVisible() && pane.getDocument().getLength() > 0) {
					caret.setVisible(true);
				}

				// TODO Add support for Token selection listeners.
				Token token = getTokenAt(e.getDot() + 1);
				if (token != selectedToken) {
					selectedToken = token;
					for (TokenSelectionListener listener : tokenSelectionListeners) {
						listener.selectedToken(selectedToken);
					}
				}
			}
		});

		// new LinePainter(pane, new Color(238, 245, 254));
		new LinePainter(pane, new Color(230, 240, 254));
		new TokenHighlighter(this, pane);

		scroll = new JScrollPane(pane);

		LineNumberView linenumbers = new LineNumberView(pane);
		linenumbers.setForeground(Color.GRAY);
		linenumbers.setFont(new Font("Courier", Font.PLAIN, 14));
		scroll.setRowHeaderView(linenumbers);

		scroll.setBorder(null);

		pane.setCaretPosition(0);

		setLayout(new BorderLayout());
		add(scroll, BorderLayout.CENTER);
	}

	private static Style defaultStyle = null;

	private static Style getDefaultStyle(StyledDocument document) {
		if (defaultStyle == null) {
			Style style = defaultStyle = document.addStyle("default", null);

			StyleConstants.setItalic(style, false);
			StyleConstants.setBold(style, false);
			StyleConstants.setFontFamily(style, "Courier");
			StyleConstants.setFontSize(style, 14);
			// StyleConstants.setBackground(style, Color.WHITE);
			StyleConstants.setForeground(style, Color.GRAY);
		}

		return defaultStyle;
	}

	private static Style landStyle = null;

	private static Style getLandStyle(StyledDocument document) {
		if (landStyle == null) {
			Style style = landStyle = document.addStyle("land", null);

			StyleConstants.setItalic(style, false);
			StyleConstants.setBold(style, false);
			StyleConstants.setFontFamily(style, "Courier");
			StyleConstants.setFontSize(style, 14);
			// StyleConstants.setBackground(style, Color.WHITE);
			StyleConstants.setForeground(style, Color.BLACK);
		}

		return landStyle;
	}

	private static Style commentStyle = null;

	private static Style getCommentStyle(StyledDocument document) {
		if (commentStyle == null) {
			Style style = commentStyle = document.addStyle("comment", null);

			StyleConstants.setItalic(style, false);
			StyleConstants.setBold(style, false);
			StyleConstants.setFontFamily(style, "Courier");
			StyleConstants.setFontSize(style, 14);
			// StyleConstants.setBackground(style, Color.WHITE);
			StyleConstants.setForeground(style, new Color(77, 144, 114));
		}

		return commentStyle;
	}

	private static Style compilerDirectiveStyle = null;

	private static Style getCompilerDirectiveStyle(StyledDocument document) {
		if (compilerDirectiveStyle == null) {
			Style style = compilerDirectiveStyle = document.addStyle(
					"compilerDirective", null);

			StyleConstants.setItalic(style, false);
			StyleConstants.setBold(style, false);
			StyleConstants.setFontFamily(style, "Courier");
			StyleConstants.setFontSize(style, 14);
			StyleConstants.setBackground(style, new Color(77, 144, 114));
			StyleConstants.setForeground(style, Color.WHITE);
		}

		return compilerDirectiveStyle;
	}

	private static Style stringStyle = null;

	private static Style getStringStyle(StyledDocument document) {
		if (stringStyle == null) {
			Style style = stringStyle = document.addStyle("comment", null);

			StyleConstants.setItalic(style, false);
			StyleConstants.setBold(style, false);
			StyleConstants.setFontFamily(style, "Courier");
			StyleConstants.setFontSize(style, 14);
			// StyleConstants.setBackground(style, Color.WHITE);
			StyleConstants.setForeground(style, new Color(59, 49, 255));
		}

		return stringStyle;
	}

	private String getContents(File file) throws IOException {
		StringBuilder builder = new StringBuilder();

		BufferedReader br = null;

		lineOffsets.clear();

		int offset = 0;
		lineOffsets.add(offset);

		try {
			br = new BufferedReader(new FileReader(file));

			char[] buffer = new char[1024];
			int len;

			while ((len = br.read(buffer)) > 0) {
				builder.append(buffer, 0, len);

				for (int i = 0; i < len; i++) {
					if (buffer[i] == '\n') {
						if (buffer[i] == '\r')
							lineOffsets.add(offset + i + 3);
						else
							lineOffsets.add(offset + i + 2);
					}
				}

				offset += len;
			}

			return builder.toString();

		} finally {
			if (br != null)
				br.close();
		}
	}

	public void scrollTo(int positionInFile) {
		if (positionInFile > 0) {
			pane.setCaretPosition(positionInFile - 1);
		} else {
			pane.setCaretPosition(0);
		}

		centerLineWithCaretInScrollPane();

		pane.requestFocus();
	}

	public void scrollToLine(int line) {
		if (line < 0 || line >= lineOffsets.size())
			throw new IllegalArgumentException("No such line number: " + line);

		scrollTo(lineOffsets.get(line));
	}

	private void centerLineWithCaretInScrollPane() {
		// Based on:
		// http://forums.sun.com/thread.jspa?threadID=5358397&start=15&tstart=0
		try {
			JViewport viewport = scroll.getViewport();
			Rectangle r = pane.modelToView(pane.getCaretPosition());

			if (r == null)
				return;

			int extentHeight = viewport.getExtentSize().height;
			int viewHeight = viewport.getViewSize().height;
			int y = Math.max(0, r.y - (extentHeight / 2));
			y = Math.min(y, viewHeight - extentHeight);
			viewport.setViewPosition(new Point(0, y));

		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	public void beforeParsing(File file, CobolParser config) {
		pane.setText("");
		pane.getHighlighter().removeAllHighlights();

		// this.tokenizer = new SourceViewIntermediateTokenizer();
		this.sink = new SourceViewSink();

		// config.addIntermediateTokenizer(this.tokenizer);
		config.addTokenSink(this.sink);
	}

	public void afterParsing(File file, ParseResults results) {
		try {
			final StyledDocument document = (StyledDocument) pane.getDocument();

			document.insertString(0, getContents(file),
					getDefaultStyle(document));

			pane.setCaretPosition(0);

			this.tokenTracker = results.getTokenTracker();

			for (Token token : this.tokenTracker.getTokens()) {
				if (token.hasTag(AreaTag.PROGRAM_TEXT_AREA)) {
					continue;
				}

				final int start = token.getStart().getPositionInFile() - 1;
				final int end = token.getEnd().getPositionInFile();
				final int len = end - start;

				if (token.hasTag(AreaTag.COMPILER_DIRECTIVE))
					document.setCharacterAttributes(start, len,
							getCompilerDirectiveStyle(document), false);
				else
					document.setCharacterAttributes(start, len,
							getCommentStyle(document), false);
			}

			// for (List<Token> line : tokenizer.getLines()) {
			// for (Token token : line) {
			// final int start = token.getStart().getPositionInFile() - 1;
			// final int end = token.getEnd().getPositionInFile();
			// final int len = end - start;
			//
			// document.setCharacterAttributes(start, len,
			// getCommentStyle(document), false);
			// }
			// }

			final List<List<Token>> lines = sink.getLines();
			for (List<Token> line : lines) {
				for (Token token : line) {
					final int start = token.getStart().getPositionInFile() - 1;
					final int end = token.getEnd().getPositionInFile();
					final int len = end - start;

					if (token.hasTag(SyntacticTag.STRING_LITERAL)) {
						document.setCharacterAttributes(start, len,
								getStringStyle(document), false);

						// } else if
						// (token.hasTag(SyntacticTag.DECIMAL_LITERAL)) {
						// document.setCharacterAttributes(start, len,
						// getStringStyle(document), false);
						//
						// } else if
						// (token.hasTag(SyntacticTag.INTEGER_LITERAL)) {
						// document.setCharacterAttributes(start, len,
						// getStringStyle(document), false);
						//
						// } else if (token.hasTag(SyntacticTag.PSEUDO_LITERAL))
						// {
						// document.setCharacterAttributes(start, len,
						// getStringStyle(document), false);

					} else {
						document.setCharacterAttributes(start, len,
								getLandStyle(document), false);
					}
				}
			}

			final Highlighter highlighter = pane.getHighlighter();
			final HighlightPainter warningPainter = new SquiggleUnderlineHighlightPainter(
					Color.ORANGE);
			final HighlightPainter errorPainter = new SquiggleUnderlineHighlightPainter(
					Color.RED);

			for (int i = 0; i < results.getWarningCount(); i++) {
				final Token token = results.getWarning(i).getFirst();
				final int start = token.getStart().getPositionInFile() - 1;
				final int end = token.getEnd().getPositionInFile();
				final int len = end - start;

				// document.setCharacterAttributes(start, len,
				// getWarningStyle(document), false);
				highlighter.addHighlight(start, start + len, warningPainter);
			}

			for (int i = 0; i < results.getErrorCount(); i++) {
				final Token token = results.getError(i).getFirst();
				final int start = token.getStart().getPositionInFile() - 1;
				final int end = token.getEnd().getPositionInFile();
				final int len = end - start;

				// document.setCharacterAttributes(start, len,
				// getErrorStyle(document), false);
				highlighter.addHighlight(start, start + len, errorPainter);
			}

		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Token getTokenAt(int position) {
		if (this.tokenTracker == null) {
			return null;

		} else {
			return this.tokenTracker.getTokenAt(position);
		}
	}

	public void addTokenSelectionListener(TokenSelectionListener listener) {
		this.tokenSelectionListeners.add(listener);
	}

	public int getNumberOfLines() {
		return lineOffsets.size();
	}

	public boolean find(String search) {
		String text = pane.getText();

		if (text == null)
			return false;

		final int fromIndex = pane.getCaretPosition() + 1;
		int index = text.indexOf(search, fromIndex);

		boolean found = index >= 0;

		if (!found && fromIndex > 0) {
			// Wrap search...
			index = text.indexOf(search);
			found = index >= 0;
		}

		if (found)
			scrollTo(index + 1);

		return found;
	}
}