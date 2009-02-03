package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.file.FileLookupInfoProvider;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author spleaner
 */
public class FileInfoManager implements Disposable {
  private final Map<FileType, FileLookupInfoProvider> myFileType2InfoProvider = new HashMap<FileType, FileLookupInfoProvider>();

  public FileInfoManager() {
    final FileLookupInfoProvider[] providers = FileLookupInfoProvider.EP_NAME.getExtensions();
    for (final FileLookupInfoProvider provider : providers) {
      final FileType[] types = provider.getFileTypes();
      for (FileType type : types) {
        myFileType2InfoProvider.put(type, provider);
      }
    }
  }

  public static FileInfoManager getFileInfoManager() {
    return ServiceManager.getService(FileInfoManager.class);
  }

  @Nullable
  public static Object getFileLookupItem(PsiElement psiElement) {
    if (!(psiElement instanceof PsiFile) || !(psiElement.isPhysical())) {
      return psiElement;
    }

    final PsiFile file = (PsiFile)psiElement;
    return getFileInfoManager()._getLookupItem(file, file.getName(), file.getFileType().getIcon());
  }

  @Nullable
  public static String getFileAdditionalInfo(PsiElement psiElement) {
    return getFileInfoManager()._getInfo(psiElement);
  }

  @Nullable
  private String _getInfo(PsiElement psiElement) {
    if (!(psiElement instanceof PsiFile) || !(psiElement.isPhysical())) {
      return null;
    }

    final PsiFile psiFile = (PsiFile)psiElement;
    final FileLookupInfoProvider provider = myFileType2InfoProvider.get(psiFile.getFileType());
    if (provider != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        final Pair<String, String> info = provider.getLookupInfo(virtualFile);
        return info == null ? null : info.second;
      }
    }

    return null;
  }

  @Nullable
  public static Object getFileLookupItem(PsiElement psiElement, String encoded, Icon icon) {
    if (!(psiElement instanceof PsiFile) || !(psiElement.isPhysical())) {
      final LookupItem result = new LookupItem(psiElement, encoded);
      result.setIcon(icon);
      return result;
    }

    return getFileInfoManager()._getLookupItem((PsiFile)psiElement, encoded, icon);
  }

  @Nullable
  public Object _getLookupItem(@NotNull final PsiFile file, String name, Icon icon) {
    final LookupItem result = new LookupItem(file, name);
    result.setIcon(icon);

    final String info = _getInfo(file);
    if (info != null) {
      result.setTailText(String.format(" (%s)", info), true);
    }

    return result;
  }

  public void dispose() {
    myFileType2InfoProvider.clear();
  }
}
