package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.CharTable;

public class ShiftIndentInsideHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.Helper");

  private final CodeStyleSettings mySettings;
  private final FileType myFileType;
  private final Helper myIndentHelper;

  public ShiftIndentInsideHelper(FileType fileType, Project project) {
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myFileType = fileType;
    myIndentHelper = HelperFactory.createHelper(fileType, project);
  }

  private static int getStartOffset(ASTNode root, ASTNode child) {
    if (child == root) return 0;
    ASTNode parent = child.getTreeParent();
    int offset = 0;
    for (ASTNode child1 = parent.getFirstChildNode(); child1 != child; child1 = child1.getTreeNext()) {
      offset += child1.getTextLength();
    }
    return getStartOffset(root, parent) + offset;
  }

  public ASTNode shiftIndentInside(ASTNode element, int indentShift) {
    if (indentShift == 0) return element;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(element);
    String text = element.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      char c = text.charAt(offset);
      if (c == '\n' || c == '\r') {
        int offset1;
        for (offset1 = offset + 1; offset1 < text.length(); offset1++) {
          c = text.charAt(offset1);
          if (c != ' ' && c != '\t') break;
        }
                    if (c == '\n' || c == '\r') continue;
        String space = text.substring(offset + 1, offset1);
        int indent = myIndentHelper.getIndent(space, true);
        int newIndent = indent + indentShift;
        newIndent = Math.max(newIndent, 0);
        String newSpace = myIndentHelper.fillIndent(newIndent);

        ASTNode leaf = element.findLeafElementAt(offset);
        if (!mayShiftIndentInside(leaf)) {
          LOG.error("Error",
                    leaf.getElementType().toString(),
                    "Type: " + leaf.getElementType() + " text: " + leaf.getText()
                    );
        }

        if (offset1 < text.length()) {
          ASTNode next = element.findLeafElementAt(offset1);
          if ((next.getElementType() == ElementType.END_OF_LINE_COMMENT
               || next.getElementType() == ElementType.C_STYLE_COMMENT
               || next.getElementType() == JspElementType.JSP_COMMENT
          ) &&
              next != element) {
            if (mySettings.KEEP_FIRST_COLUMN_COMMENT) {
              int commentIndent = myIndentHelper.getIndent(next, true);
              if (commentIndent == 0) continue;
            }
          }
          else if (next.getElementType() == XmlElementType.XML_DATA_CHARACTERS) {
            continue;
          }
        }

        int leafOffset = getStartOffset(element, leaf);
        if (leaf.getElementType() == ElementType.DOC_COMMENT_DATA && leafOffset + leaf.getTextLength() == offset + 1) {
          ASTNode next = element.findLeafElementAt(offset + 1);
          if (next.getElementType() == TokenType.WHITE_SPACE) {
            leaf = next;
            leafOffset = getStartOffset(element, leaf);
          }
          else {
            if (newSpace.length() > 0) {
              LeafElement newLeaf = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, newSpace, 0, newSpace.length(), charTableByTree,
                                                                    SharedImplUtil.getManagerByTree(next));
              next.getTreeParent().addChild(newLeaf, next);
            }
            text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
            continue;
          }
        }

        int startOffset = offset + 1 - leafOffset;
        int endOffset = offset1 - leafOffset;
        if (!LOG.assertTrue(0 <= startOffset && startOffset <= endOffset && endOffset <= leaf.getTextLength())) {
          continue;
        }
        String leafText = leaf.getText();
        String newLeafText = leafText.substring(0, startOffset) + newSpace + leafText.substring(endOffset);
        if (newLeafText.length() > 0) {
          LeafElement newLeaf = Factory.createSingleLeafElement(leaf.getElementType(), newLeafText, 0,
                                                                newLeafText.length(), charTableByTree, SharedImplUtil.getManagerByTree(leaf));
          if (leaf.getTreeParent() != null) {
            leaf.getTreeParent().replaceChild(leaf, newLeaf);
          }
          if (leaf == element) {
            element = newLeaf;
          }
        }
        else {
          ASTNode parent = leaf.getTreeParent();
          if (parent != null) {
            parent.removeChild(leaf);
          }
        }
        text = text.substring(0, offset + 1) + newSpace + text.substring(offset1);
      }
    }
    return element;
  }

  public static boolean mayShiftIndentInside(final ASTNode leaf) {
    return (isComment(leaf) && !checkJspTexts(leaf))
           || leaf.getElementType() == TokenType.WHITE_SPACE
           || leaf.getElementType() == XmlElementType.XML_DATA_CHARACTERS
           || leaf.getElementType() == JspElementType.JAVA_CODE
           || leaf.getElementType() == JspElementType.JSP_SCRIPTLET
           || leaf.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean checkJspTexts(final ASTNode leaf) {
    ASTNode child = leaf.getFirstChildNode();
    while(child != null){
      if(child instanceof OuterLanguageElement) return true;
      child = child.getTreeNext();
    }
    return false;
  }

  private static  boolean isComment(final ASTNode node) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(node);
    if (psiElement instanceof PsiComment) return true;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(psiElement.getLanguage());
    if (parserDefinition == null) return false;
    final TokenSet commentTokens = parserDefinition.getCommentTokens();
    return commentTokens.contains(node.getElementType());
  }

  public FileType getFileType() {
    return myFileType;
  }
}
