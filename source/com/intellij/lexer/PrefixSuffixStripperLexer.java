/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharArrayCharSequence;

public class PrefixSuffixStripperLexer extends LexerBase {
  private CharSequence myBuffer;
  private char[] myBufferArray;
  private int myTokenStart;
  private int myTokenEnd;
  private IElementType myTokenType;
  private int myState;
  private int myBufferEnd;
  private final String myPrefix;
  private final IElementType myPrefixType;
  private final String mySuffix;
  private final IElementType myMiddleTokenType;
  private final IElementType mySuffixType;

  public PrefixSuffixStripperLexer(final String prefix,
                                   final IElementType prefixType,
                                   final String suffix,
                                   final IElementType suffixType,
                                   final IElementType middleTokenType) {
    mySuffixType = suffixType;
    myMiddleTokenType = middleTokenType;
    mySuffix = suffix;
    myPrefixType = prefixType;
    myPrefix = prefix;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(new CharArrayCharSequence(buffer),startOffset, endOffset, initialState);
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myTokenStart = startOffset;
    myTokenEnd = startOffset;
    myTokenType = null;
    myState = initialState;
    myBufferEnd = endOffset;
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public int getTokenStart() {
    locateToken();
    return myTokenStart;
  }

  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  public int getState() {
    return myState;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

  public char[] getBuffer() {
    return CharArrayUtil.fromSequence(myBuffer);
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public void advance() {
    myTokenType = null;
  }

  private void locateToken() {
    if (myTokenType != null || myState == 3) return;

    if (myState == 0) {
      myTokenEnd = myTokenStart + myPrefix.length();
      myTokenType = myPrefixType;
      myState = myTokenEnd < myBufferEnd ? 1 : 3;
      return;
    }

    if (myState == 1) {
      myTokenStart = myTokenEnd;
      final int suffixStart = myBufferEnd - mySuffix.length();
      myTokenType = myMiddleTokenType;
      if ( (myBufferArray != null && CharArrayUtil.regionMatches(myBufferArray, suffixStart, myBufferEnd, mySuffix)) ||
           (myBufferArray == null && CharArrayUtil.regionMatches(myBuffer, suffixStart, myBufferEnd, mySuffix))
         ) {
        myTokenEnd = suffixStart;
        if (myTokenStart < myTokenEnd) {
          myState = 2;
        }
        else {
          myState = 3;
          myTokenType = mySuffixType;
          myTokenEnd = myBufferEnd;
        }
      }
      else {
        myTokenEnd = myBufferEnd;
        myState = 3;
      }

      return;
    }

    if (myState == 2) {
      myTokenStart = myTokenEnd;
      myTokenEnd = myBufferEnd;
      myTokenType = mySuffixType;
      myState = 3;
    }
  }
}
