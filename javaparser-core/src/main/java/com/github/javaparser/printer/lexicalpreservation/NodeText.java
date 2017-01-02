/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2016 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.printer.lexicalpreservation;

import com.github.javaparser.ASTParserConstants;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * This contains the lexical information for a single node.
 * It is basically a list of tokens and children.
 */
class NodeText {
    private LexicalPreservingPrinter lexicalPreservingPrinter;
    private List<TextElement> elements;

    //
    // Constructors
    //

    NodeText(LexicalPreservingPrinter lexicalPreservingPrinter, List<TextElement> elements) {
        this.lexicalPreservingPrinter = lexicalPreservingPrinter;
        this.elements = elements;
    }

    /**
     * Initialize with an empty list of elements.
     */
    NodeText(LexicalPreservingPrinter lexicalPreservingPrinter) {
        this(lexicalPreservingPrinter, new LinkedList<>());
    }

    //
    // Adding elements
    //

    /**
     * Add an element at the end.
     */
    void addElement(TextElement nodeTextElement) {
        this.elements.add(nodeTextElement);
    }

    /**
     * Add an element at the given position.
     */
    void addElement(int index, TextElement nodeTextElement) {
        this.elements.add(index, nodeTextElement);
    }

    void addChild(Node child) {
        addElement(new ChildTextElement(lexicalPreservingPrinter, child));
    }

    void addChild(int index, Node child) {
        addElement(index, new ChildTextElement(lexicalPreservingPrinter, child));
    }

    void addToken(int tokenKind, String text) {
        elements.add(new TokenTextElement(tokenKind, text));
    }

    //
    // Finding elements
    //

    int findElement(TextElementMatcher matcher) {
        return findElement(matcher, 0);
    }

    int findElement(TextElementMatcher matcher, int from) {
        for (int i=from; i<elements.size(); i++) {
            TextElement element = elements.get(i);
            if (matcher.match(element)) {
                return i;
            }
        }
        throw new IllegalArgumentException(String.format("I could not find child '%s' from position %i", matcher, from));
    }

    int findChild(Node child) {
        return findChild(child, 0);
    }

    int findChild(Node child, int from) {
        return findElement(TextElementMatchers.byNode(child), from);
    }

    private int findToken(int tokenKind) {
        return findToken(tokenKind, 0);
    }

    private int findToken(int tokenKind, int from) {
        return findElement(TextElementMatchers.byTokenType(tokenKind), from);
    }

    //
    // Removing single elements
    //

    void remove(TextElementMatcher matcher) {
        elements.removeIf(e -> matcher.match(e));
    }

    public void remove(TextElementMatcher matcher, boolean potentiallyFollowingWhitespace) {
        int i=0;
        for (TextElement e : elements) {
            if (matcher.match(e)) {
                elements.remove(e);
                if (potentiallyFollowingWhitespace) {
                    if (i < elements.size()) {
                        if (elements.get(i).isToken(1)) {
                            elements.remove(i);
                        }
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
                return;
            }
        }
        throw new IllegalArgumentException();
    }

    //
    // Removing sequences
    //

    public void removeFromToken(TextElementMatcher start, boolean includingPreceedingSpace) {
        removeFromTokenUntil(start, Optional.empty(), includingPreceedingSpace);
    }

    public void removeFromTokenUntil(TextElementMatcher start, Optional<Integer> stopTokenKind, boolean includingPreceedingSpace) {
        for (int i=elements.size() -1; i>=0; i--) {
            if (start.match(elements.get(i))) {
                while (elements.size() > i && (!stopTokenKind.isPresent() || !elements.get(i).isToken(stopTokenKind.get()))) {
                    elements.remove(i);
                }
                if (includingPreceedingSpace && elements.get(i - 1).isToken(Tokens.space().getTokenKind())) {
                    elements.remove(i - 1);
                }
                return;
            }
        }
        throw new IllegalArgumentException();
    }

    void removeAllBefore(TextElementMatcher delimiter) {
        int index = findElement(delimiter);
        for (int i=0;i<index;i++) {
            elements.remove(0);
        }
    }

    void removeElement(int index) {
        elements.remove(index);
    }

    void removeWhiteSpaceFollowing(TextElementMatcher delimiter) {
        int index = findElement(delimiter);
        ++index;
        while (index < elements.size() && (elements.get(index).isToken(1)||elements.get(index).isToken(3))) {
            elements.remove(index);
        }
    }

    void removeComment(Comment comment) {
        for (int i=0;i<elements.size();i++){
            TextElement e = elements.get(i);
            if (e.isCommentToken() && e.expand().trim().equals(comment.toString().trim())) {
                elements.remove(i);
                if (i<elements.size() && elements.get(i).isToken(3)) {
                    elements.remove(i);
                }
                return;
            }
        }
    }

    void removeTextBetween(TextElementMatcher start, TextElementMatcher end) {
        removeTextBetween(start, end, false);
    }

    /**
     * Remove all elements between the given token (inclusive) and the given child (exclusive).
     */
    void removeTextBetween(TextElementMatcher start, TextElementMatcher end, boolean removeSpaceImmediatelyAfter) {
        int startDeletion = findElement(start);
        int endDeletion = findElement(end, startDeletion + 1);
        if (removeSpaceImmediatelyAfter && (getTextElement(endDeletion + 1) instanceof TokenTextElement) &&
                ((TokenTextElement) getTextElement(endDeletion + 1)).getTokenKind() == Tokens.whitespaceTokenKind()) {
            endDeletion++;
        }
        removeBetweenIndexes(startDeletion, endDeletion);
    }

    void removeTextBetween(int startTokenKind, int endTokenKind) {
        int startDeletion = findToken(startTokenKind, 0);
        int endDeletion = findToken(endTokenKind, startDeletion + 1);
        removeBetweenIndexes(startDeletion+1, endDeletion-1);
    }

    private void removeBetweenIndexes(int startDeletion, int endDeletion) {
        int i = endDeletion;
        while (i >= startDeletion) {
            elements.remove(i--);
        }
    }

    void removeTextBetween(Node child, int tokenKind, boolean removeSpaceImmediatelyAfter) {
        int startDeletion = findChild(child, 0);
        int endDeletion = findToken(tokenKind, startDeletion + 1);
        if (removeSpaceImmediatelyAfter && (getTextElement(endDeletion + 1) instanceof TokenTextElement) &&
                ((TokenTextElement) getTextElement(endDeletion + 1)).getTokenKind() == Tokens.whitespaceTokenKind()) {
            endDeletion++;
        }
        removeBetweenIndexes(startDeletion, endDeletion);
    }

    //
    // Replacing elements
    //

    public void replace(Node oldChild, Node newChild) {
        int index = findChild(oldChild, 0);
        elements.remove(index);
        elements.add(index, new ChildTextElement(lexicalPreservingPrinter, newChild));
    }

    void replaceToken(int oldToken, TokenTextElement newToken) {
        int index = findToken(oldToken);
        elements.set(index, newToken);
    }

    public void replaceComment(Comment oldValue, Comment newValue) {
        for (int i=0;i<elements.size();i++){
            TextElement e = elements.get(i);
            if (e.isCommentToken() && e.expand().trim().equals(oldValue.toString().trim())) {
                elements.remove(i);
                elements.add(i, new TokenTextElement(commentToTokenKind(newValue), newValue.toString().trim()));
                return;
            }
        }
    }

    //
    // Other methods
    //

    /**
     * Generate the corresponding string.
     */
    String expand() {
        StringBuffer sb = new StringBuffer();

        elements.forEach(e -> sb.append(e.expand()));
        return sb.toString();
    }

    // Visible for testing
    int numberOfElements() {
        return elements.size();
    }

    // Visible for testing
    TextElement getTextElement(int index) {
        return elements.get(index);
    }

    // Visible for testing
    List<TextElement> getElements() {
        return elements;
    }

    private int commentToTokenKind(Comment comment){
        if (comment instanceof JavadocComment) {
            return ASTParserConstants.JAVA_DOC_COMMENT;
        } else if (comment instanceof LineComment) {
            return ASTParserConstants.SINGLE_LINE_COMMENT;
        } else if (comment instanceof BlockComment) {
            return ASTParserConstants.MULTI_LINE_COMMENT;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "NodeText{" + elements + '}';
    }
}
