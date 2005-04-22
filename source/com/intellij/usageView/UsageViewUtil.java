package com.intellij.usageView;

import com.intellij.ant.PsiAntElement;
import com.intellij.aspects.psi.PsiAdvice;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.aspects.psi.gen.PsiErrorIntroduction;
import com.intellij.aspects.psi.gen.PsiVerificationIntroduction;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.Set;

/**
 *
 */
public class UsageViewUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageViewUtil");
  public static final String DEFAULT_PACKAGE_NAME = "<default>";

  private UsageViewUtil() { }

  public static String createNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      String name = xmlTag.getName();
      final PsiMetaData metaData = xmlTag.getMetaData();

      if (metaData != null) {
        name = metaData.getName();
      }
      else {
        name = xmlTag.getName();
      }

      return ((metaData == null)?"<" + name + ">":name) + " of file " + xmlTag.getContainingFile().getName();
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    else if (element != null) {
      FindUsagesProvider provider = element.getLanguage().getFindUsagesProvider();
      if (provider != null) {
        return provider.getNodeText(element, useFullName);
      }
    }

    return "";
  }

  public static String getPackageName(PsiDirectory directory, boolean includeRootDir) {
    PsiPackage aPackage = directory.getPackage();
    if (aPackage == null) {
      return directory.getVirtualFile().getPresentableUrl();
    }
    else {
      String packageName = getPackageName(aPackage);
      if (includeRootDir) {
        String rootDir = getRootDirectoryForPackage(directory);
        if (rootDir != null) {
          return packageName + " (in " + rootDir + ")";
        }
      }
      return packageName;
    }
  }

  public static String getRootDirectoryForPackage(PsiDirectory directory) {
    PsiManager manager = directory.getManager();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();
    VirtualFile root = fileIndex.getSourceRootForFile(virtualFile);

    if (root == null) {
      root = fileIndex.getClassRootForFile(virtualFile);
    }
    if (root != null) {
      return root.getPresentableUrl();
    }
    else {
      return null;
    }
  }

  public static String getPackageName(PsiPackage psiPackage) {
    if (psiPackage == null) {
      return null;
    }
    String name = psiPackage.getQualifiedName();
    if (name.length() > 0) {
      return name;
    }
    else {
      return DEFAULT_PACKAGE_NAME;
    }
  }

  public static String capitalize(String s) {
    if (s == null || s.length() == 0) {
      return s;
    }
    char c = Character.toUpperCase(s.charAt(0));
    if (s.length() == 1) {
      return "" + c;
    }
    else {
      return "" + c + s.substring(1);
    }
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret = "";
    if (psiElement instanceof PsiNamedElement) {
      if (psiElement instanceof XmlTag) {
        PsiMetaData metaData = ((XmlTag)psiElement).getMetaData();
        if (metaData!=null) return metaData.getName();
      }
      ret = ((PsiNamedElement)psiElement).getName();
    }
    else if (psiElement instanceof PsiThrowStatement) {
      ret = "Exception";
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiVerificationIntroduction) {
      PsiLiteralExpression message = ((PsiVerificationIntroduction)psiElement).getMessage();
      ret = message == null ? "<no message>" : (String)message.getValue();
    }
    else if (psiElement instanceof PsiAdvice) {
      ret = ((PsiAdvice)psiElement).getPointcut().getText();
    }
    return ret;
  }

  public static String getLongName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret;
    if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)psiElement).getPackage();
      if (aPackage != null) {
        ret = aPackage.getQualifiedName();
      }
      else {
        ret = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
      }
    }
    else if (psiElement instanceof PsiPackage) {
      ret = ((PsiPackage)psiElement).getQualifiedName();
    }
    else if (psiElement instanceof PsiClass) {
      if (psiElement instanceof PsiAnonymousClass) {
        ret = "anonymous class";
      }
      else {
        ret = ((PsiClass)psiElement).getQualifiedName(); // It happens for local classes
        if (ret == null) {
          ret = ((PsiClass)psiElement).getName();
        }
      }
    }
    else if (psiElement instanceof PsiVariable) {
      ret = ((PsiVariable)psiElement).getName();
    }
    else if (psiElement instanceof XmlTag) {
      ret = ((XmlTag)psiElement).getName();
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)psiElement;
      ret =
      PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    }
    else if (psiElement instanceof PsiPointcutDef) {
      PsiPointcutDef pointcutDef = (PsiPointcutDef)psiElement;
      ret =
      PsiFormatUtil.formatPointcut(pointcutDef, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                   PsiFormatUtil.SHOW_TYPE);
    }
    else if (psiElement instanceof PsiVerificationIntroduction) {
      PsiErrorIntroduction introduction = (PsiErrorIntroduction)psiElement;
      PsiLiteralExpression message = introduction.getMessage();
      ret = message == null ? "<no message>" : (String)message.getValue();
    }
    else if (psiElement instanceof PsiAdvice) {
      ret = ((PsiAdvice)psiElement).getPointcut().getText();
    }
    else {
      ret = "";
    }
    return ret;
  }

  public static String getType(PsiElement psiElement) {
    if (psiElement instanceof XmlTag) {
      final PsiMetaData metaData = ((XmlTag)psiElement).getMetaData();
      if (metaData!=null && metaData.getDeclaration() instanceof XmlTag) {
        return ((XmlTag)metaData.getDeclaration()).getName();
      }
      return "XML tag";
    }

    if (psiElement instanceof PsiAntElement) {
      return ((PsiAntElement)psiElement).getRole().getName();
    } else if (psiElement instanceof PsiFile) {
      return "file";
    } else if (psiElement instanceof PsiDirectory) {
      return "directory";
    } else if (psiElement instanceof WebDirectoryElement) {
      return "web directory";
    }

    final Language lang = psiElement.getLanguage();
    if (lang != null) {
      FindUsagesProvider provider = lang.getFindUsagesProvider();
      if (provider != null) {
        return provider.getType(psiElement);
      }
    }

    return "";
  }

  public static String getDescriptiveName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret = "";

    if (psiElement instanceof XmlTag) {
      final PsiMetaData metaData = ((XmlTag)psiElement).getMetaData();
      if (metaData!=null) return metaData.getName();
      ret = ((XmlTag)psiElement).getName();
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else {
      final Language lang = psiElement.getLanguage();
      if (lang != null) {
        FindUsagesProvider provider = lang.getFindUsagesProvider();
        if (provider != null) {
          return provider.getDescriptiveName(psiElement);
        }
      }
    }

    return ret;
  }

  public static String getUsageCountInfo(int usagesCount, int filesCount, String referenceWord) {
    String info;
    if (filesCount > 0) {
      String files = filesCount != 1 ? " files " : " file ";
      if (usagesCount > 1) {
        referenceWord += "s";
      }
      info = "( " + usagesCount + " " + referenceWord + " in " + filesCount + files + ")";
    }
    else {
      info = "( Not found )";
    }
    return info;
  }

  public static boolean hasNonCodeUsages(UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      if (usages[i].isNonCodeUsage) return true;
    }
    return false;
  }

  public static boolean hasReadOnlyUsages(UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!usage.isWritable()) return true;
    }
    return false;
  }

  public static UsageInfo[] removeDuplicatedUsages(UsageInfo usages[]) {
    Set<UsageInfo> set = new THashSet<UsageInfo>(Arrays.asList(usages));
    return set.toArray(new UsageInfo[set.size()]);
  }
}