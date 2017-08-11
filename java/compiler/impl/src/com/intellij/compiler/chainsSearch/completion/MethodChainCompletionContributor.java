/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.chainsSearch.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.backwardRefs.ReferenceIndexUnavailableException;
import com.intellij.compiler.chainsSearch.ChainSearchMagicConstants;
import com.intellij.compiler.chainsSearch.ChainSearcher;
import com.intellij.compiler.chainsSearch.CallChain;
import com.intellij.compiler.chainsSearch.MethodChainLookupRangingHelper;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.chainsSearch.context.ChainSearchTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class MethodChainCompletionContributor extends CompletionContributor {
  public static final String REGISTRY_KEY = "compiler.ref.chain.search";
  private static final Logger LOG = Logger.getInstance(MethodChainCompletionContributor.class);
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();

  public MethodChainCompletionContributor() {
    ElementPattern<PsiElement> pattern = or(patternForMethodCallArgument(), patternForVariableAssignment(), patternForReturnExpression());
    extend(CompletionType.SMART, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        try {
          if (!Registry.is(REGISTRY_KEY)) return;
          final Set<PsiMethod> alreadySuggested = new THashSet<>();
          CompletionResultSet finalResult = result;
          result.runRemainingContributors(parameters, completionResult -> {
            LookupElement lookupElement = completionResult.getLookupElement();
            PsiElement psi = lookupElement.getPsiElement();
            if (psi instanceof PsiMethod) {
              alreadySuggested.add((PsiMethod)psi);
            }
            finalResult.passResult(completionResult);
          });
          ChainCompletionContext completionContext = extractContext(parameters);
          if (completionContext == null) return;
          result = JavaCompletionSorting.addJavaSorting(parameters, result);
          List<LookupElement> elementsFoundByMethodsChainsSearch = searchForLookups(completionContext);
          if (!UNIT_TEST_MODE && !alreadySuggested.isEmpty()) {
            elementsFoundByMethodsChainsSearch = elementsFoundByMethodsChainsSearch.stream().filter(lookupElement -> {
              PsiElement psi = lookupElement.getPsiElement();
              return !(psi instanceof PsiMethod) || !alreadySuggested.contains(psi);
            }).collect(Collectors.toList());
          }
          result.addAllElements(elementsFoundByMethodsChainsSearch);
        }
        catch (ReferenceIndexUnavailableException ignored) {
          //index was closed due to compilation
        }
      }
    });
  }

  private static List<LookupElement> searchForLookups(ChainCompletionContext context) {
    CompilerReferenceServiceEx methodsUsageIndexReader = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(context.getProject());
    ChainSearchTarget target = context.getTarget();
    List<CallChain> searchResult =
      ChainSearcher.search(ChainSearchMagicConstants.MAX_CHAIN_SIZE,
                           target,
                           ChainSearchMagicConstants.MAX_SEARCH_RESULT_SIZE,
                           context,
                           methodsUsageIndexReader);
    int maxWeight = searchResult.stream().mapToInt(CallChain::getChainWeight).max().orElse(0);

    return searchResult
      .stream()
      .filter(ch -> ch.getChainWeight() * ChainSearchMagicConstants.FILTER_RATIO >= maxWeight)
      .map(ch -> MethodChainLookupRangingHelper.toLookupElement(ch, context))
      .collect(Collectors.toList());
  }

  @Nullable
  private static ChainCompletionContext extractContext(CompletionParameters parameters) {
    PsiElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(),
                                                    PsiAssignmentExpression.class,
                                                    PsiLocalVariable.class,
                                                    PsiMethodCallExpression.class,
                                                    PsiReturnStatement.class);
    LOG.assertTrue(parent != null, "A completion position should match to a pattern");

    if (parent instanceof PsiReturnStatement) {
      return extractContextFromReturn((PsiReturnStatement)parent, parameters);
    }
    if (parent instanceof PsiAssignmentExpression) {
      return extractContextFromAssignment((PsiAssignmentExpression)parent, parameters);
    }
    if (parent instanceof PsiLocalVariable) {
      return extractContextFromVariable((PsiLocalVariable)parent, parameters);
    }
    return extractContextFromMethodCall((PsiMethodCallExpression)parent, parameters);
  }

  @Nullable
  private static ChainCompletionContext extractContextFromMethodCall(PsiMethodCallExpression parent,
                                                                     CompletionParameters parameters) {
    PsiMethod method = parent.resolveMethod();
    if (method == null) return null;
    PsiExpression expression = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpressionList.class);
    if (expressionList == null) return null;
    int exprPosition = Arrays.asList(expressionList.getExpressions()).indexOf(expression);
    PsiParameter[] methodParameters = method.getParameterList().getParameters();
    if (exprPosition < methodParameters.length) {
      PsiParameter methodParameter = methodParameters[exprPosition];
      return ChainCompletionContext.createContext(methodParameter.getType(), PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class), suggestIterators(parameters));
    }
    return null;
  }

  @Nullable
  private static ChainCompletionContext extractContextFromReturn(PsiReturnStatement returnStatement,
                                                                 CompletionParameters parameters) {
    PsiType type = PsiTypesUtil.getMethodReturnType(returnStatement);
    if (type == null) return null;
    return ChainCompletionContext.createContext(type, returnStatement, suggestIterators(parameters));
  }

  @Nullable
  private static ChainCompletionContext extractContextFromVariable(PsiLocalVariable localVariable,
                                                                   CompletionParameters parameters) {
    PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(localVariable, PsiDeclarationStatement.class);
    return ChainCompletionContext.createContext(localVariable.getType(), declaration, suggestIterators(parameters));
  }

  @Nullable
  private static ChainCompletionContext extractContextFromAssignment(PsiAssignmentExpression assignmentExpression,
                                                                     CompletionParameters parameters) {
    PsiExpression lExpr = assignmentExpression.getLExpression();
    if (!(lExpr instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression)lExpr).resolve();
    return resolved instanceof PsiVariable
           ? ChainCompletionContext.createContext(((PsiVariable)resolved).getType(), assignmentExpression, suggestIterators(parameters))
           : null;
  }

  @NotNull
  private static ElementPattern<PsiElement> patternForVariableAssignment() {
    final ElementPattern<PsiElement> patternForParent = or(psiElement().withText(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
                                                             .afterSiblingSkipping(psiElement(PsiWhiteSpace.class),
                                                                                   psiElement(PsiJavaToken.class).withText("=")));

    return psiElement().withParent(patternForParent).withSuperParent(2, or(psiElement(PsiAssignmentExpression.class),
                                                                           psiElement(PsiLocalVariable.class)
                                                                             .inside(PsiDeclarationStatement.class))).inside(PsiMethod.class);
  }

  @NotNull
  private static ElementPattern<PsiElement> patternForMethodCallArgument() {
    return psiElement().withSuperParent(3, PsiMethodCallExpression.class);
  }

  private static ElementPattern<PsiElement> patternForReturnExpression() {
    return psiElement().withSuperParent(2, PsiReturnStatement.class);
  }

  private static boolean suggestIterators(@NotNull CompletionParameters parameters) {
    return parameters.getInvocationCount() > 1;
  }
}