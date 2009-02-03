package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class JavaSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor");

  private static final Fixer[] ourFixers;
  private static final EnterProcessor[] ourEnterProcessors;

  static {
    final List<Fixer> fixers = new ArrayList<Fixer>();
    fixers.add(new LiteralFixer());
    fixers.add(new MethodCallFixer());
    fixers.add(new IfConditionFixer());
    fixers.add(new WhileConditionFixer());
    fixers.add(new CatchDeclarationFixer());
    fixers.add(new SwitchExpressionFixer());
    fixers.add(new DoWhileConditionFixer());
    fixers.add(new BlockBraceFixer());
    fixers.add(new MissingIfBranchesFixer());
    fixers.add(new MissingWhileBodyFixer());
    fixers.add(new MissingSwitchBodyFixer());
    fixers.add(new MissingCatchBodyFixer());
    fixers.add(new MissingSynchronizedBodyFixer());
    fixers.add(new MissingForBodyFixer());
    fixers.add(new MissingForeachBodyFixer());
    fixers.add(new ParameterListFixer());
    fixers.add(new MissingMethodBodyFixer());
    fixers.add(new MissingReturnExpressionFixer());
    fixers.add(new MissingThrowExpressionFixer());
    fixers.add(new ParenthesizedFixer());
    fixers.add(new SemicolonFixer());
    fixers.add(new MissingArrayInitializerBraceFixer());
    fixers.add(new EnumFieldFixer());
    //ourFixers.add(new CompletionFixer());
    ourFixers = fixers.toArray(new Fixer[fixers.size()]);

    List<EnterProcessor> processors = new ArrayList<EnterProcessor>();
    processors.add(new CommentBreakerEnterProcessor());
    processors.add(new AfterSemicolonEnterProcessor());
    processors.add(new BreakingControlFlowEnterProcessor());
    processors.add(new PlainEnterProcessor());
    ourEnterProcessors = processors.toArray(new EnterProcessor[processors.size()]);
  }

  private int myFirstErrorOffset = Integer.MAX_VALUE;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  public static class TooManyAttemptsException extends Exception {}

  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    final Document document = editor.getDocument();
    final String textForRollback = document.getText();
    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
      myFirstErrorOffset = Integer.MAX_VALUE;
      process(project, editor, psiFile, 0);
    }
    catch (TooManyAttemptsException e) {
      document.replaceString(0, document.getTextLength(), textForRollback);
    } finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }


  private void process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, final int attempt) throws TooManyAttemptsException {
    if (attempt > MAX_ATTEMPTS) throw new TooManyAttemptsException();

    try {
      commit(editor);
      if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }

      myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement atCaret = getStatementAtCaret(editor, file);
      if (atCaret == null) {
        if (!new CommentBreakerEnterProcessor().doEnter(editor, file, false)) {
          plainEnter(editor);
        }
        return;
      }

      List<PsiElement> queue = new ArrayList<PsiElement>();
      collectAllElements(atCaret, queue, true);
      queue.add(atCaret);

      for (PsiElement psiElement : queue) {
        for (Fixer fixer : ourFixers) {
          fixer.apply(editor, this, psiElement);
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(project) || !psiElement.isValid()) {
            moveCaretInsideBracesIfAny(editor, file);
            process(project, editor, file, attempt + 1);
            return;
          }
        }
      }

      doEnter(atCaret, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  @Override
  protected void reformat(PsiElement atCaret) throws IncorrectOperationException {
    PsiElement parent = atCaret.getParent();
    if (parent instanceof PsiCodeBlock) {
      final PsiCodeBlock block = (PsiCodeBlock) parent;
      if (block.getStatements().length > 0 && block.getStatements()[0] == atCaret) {
        atCaret = block;
      }
    }
    else if (parent instanceof PsiForStatement) {
      atCaret = parent;
    }

    super.reformat(atCaret);
  }


  private void doEnter(PsiElement atCaret, Editor editor) throws IncorrectOperationException {
    final PsiFile psiFile = atCaret.getContainingFile();

    final RangeMarker rangeMarker = createRangeMarker(atCaret);
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      reformat(atCaret);
      return;
    }

    reformat(atCaret);
    commit(editor);

    atCaret = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), atCaret.getClass());
    for (EnterProcessor processor : ourEnterProcessors) {
      if(atCaret == null){
        // Can't restore element at caret after enter processor execution!
        break;
      }

      if (processor.doEnter(editor, atCaret, isModified(editor))) return;
    }

    if (!isModified(editor)) {
      plainEnter(editor);
    } else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(rangeMarker.getEndOffset());
      } else {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }
    }
  }

  private static void collectAllElements(PsiElement atCaret, List<PsiElement> res, boolean recurse) {
    res.add(0, atCaret);
    if (doNotStepInto(atCaret)) {
      if (!recurse) return;
      recurse = false;
    }

    final PsiElement[] children = atCaret.getChildren();
    for (PsiElement child : children) {
      if (atCaret instanceof PsiStatement && child instanceof PsiStatement) continue;
      collectAllElements(child, res, recurse);
    }
  }

  private static boolean doNotStepInto(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiCodeBlock || element instanceof PsiStatement || element instanceof PsiMethod;
  }

  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    final PsiElement atCaret = super.getStatementAtCaret(editor, psiFile);

    if (atCaret instanceof PsiWhiteSpace) return null;
    if (atCaret instanceof PsiJavaToken && "}".equals(atCaret.getText())) return null;

    PsiElement statementAtCaret = PsiTreeUtil.getParentOfType(atCaret,
                                                              PsiStatement.class,
                                                              PsiCodeBlock.class,
                                                              PsiMember.class,
                                                              PsiComment.class
    );

    if (statementAtCaret instanceof PsiBlockStatement) return null;

    if (statementAtCaret != null && statementAtCaret.getParent() instanceof PsiForStatement) {
      if (!PsiTreeUtil.hasErrorElements(statementAtCaret)) {
        statementAtCaret = statementAtCaret.getParent();
      }
    }

    return statementAtCaret instanceof PsiStatement ||
           statementAtCaret instanceof PsiMember
           ? statementAtCaret
           : null;
  }

  protected void moveCaretInsideBracesIfAny(@NotNull final Editor editor, @NotNull final PsiFile file) throws IncorrectOperationException {
    int caretOffset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();

    if (CharArrayUtil.regionMatches(chars, caretOffset, "{}")) {
      caretOffset+=2;
    }
    else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n}")) {
      caretOffset+=3;
    }

    caretOffset = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1;

    if (CharArrayUtil.regionMatches(chars, caretOffset - "{}".length(), "{}") ||
        CharArrayUtil.regionMatches(chars, caretOffset - "{\n}".length(), "{\n}")) {
      commit(editor);
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
      final boolean old = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
      PsiElement elt = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset - 1), PsiCodeBlock.class);
      reformat(elt);
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old;
      editor.getCaretModel().moveToOffset(caretOffset - 1);
    }
  }

  public void registerUnresolvedError(int offset) {
    if (myFirstErrorOffset > offset) {
      myFirstErrorOffset = offset;
    }
  }

  protected static void plainEnter(@NotNull final Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(@NotNull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }

}
