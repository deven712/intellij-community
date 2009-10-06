package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.BaseFileConfigurableStoreImpl");

  @NonNls protected static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  private final ComponentManager myComponentManager;
  private static final ArrayList<String> ourConversionProblemsStorage = new ArrayList<String>();
  private final DefaultsStateStorage myDefaultsStateStorage;
  private StateStorageManager myStateStorageManager;


  protected BaseFileConfigurableStoreImpl(final ComponentManager componentManager) {
    myComponentManager = componentManager;
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(myComponentManager);
    myDefaultsStateStorage = new DefaultsStateStorage(pathMacroManager);
  }

  public synchronized ComponentManager getComponentManager() {
    return myComponentManager;
  }

  protected static class BaseStorageData extends FileBasedStorage.FileStorageData {
    protected int myVersion;

    public BaseStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected BaseStorageData(BaseStorageData storageData) {
      super(storageData);

      myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      super.load(rootElement);

      final String v = rootElement.getAttributeValue(VERSION_OPTION);
      if (v != null) {
        myVersion = Integer.parseInt(v);
      }
      else {
        myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;
      }
    }

    @NotNull
    protected Element save() {
      final Element root = super.save();
      root.setAttribute(VERSION_OPTION, Integer.toString(myVersion));
      return root;
    }

    public XmlElementStorage.StorageData clone() {
      return new BaseStorageData(this);
    }

    protected int computeHash() {
      int result = super.computeHash();
      result = result*31 + myVersion;
      return result;
    }

    @Nullable
    public Set<String> getDifference(final XmlElementStorage.StorageData storageData, PathMacroSubstitutor substitutor) {
      final BaseStorageData data = (BaseStorageData)storageData;
      if (myVersion != data.myVersion) return null;
      return super.getDifference(storageData, substitutor);
    }
  }

  protected abstract XmlElementStorage getMainStorage();

  @Nullable
  static ArrayList<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  public void load() throws IOException, StateStorage.StateStorageException {
    getMainStorageData(); //load it
  }

  public BaseStorageData getMainStorageData() throws StateStorage.StateStorageException {
    return (BaseStorageData) getMainStorage().getStorageData(false);
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

  public StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  protected abstract StateStorageManager createStateStorageManager();
}
