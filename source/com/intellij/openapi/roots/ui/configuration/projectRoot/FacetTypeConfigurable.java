package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.facet.FacetType;
import com.intellij.facet.impl.ui.facetType.FacetTypeEditor;

import javax.swing.*;

/**
 * @author nik
 */
public class FacetTypeConfigurable extends NamedConfigurable<FacetType> {
  private final FacetStructureConfigurable myFacetStructureConfigurable;
  private final FacetType myFacetType;

  public FacetTypeConfigurable(final FacetStructureConfigurable facetStructureConfigurable, final FacetType facetType) {
    myFacetStructureConfigurable = facetStructureConfigurable;
    myFacetType = facetType;
  }

  public void setDisplayName(final String name) {
  }

  public FacetType getEditableObject() {
    return myFacetType;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("facet.type.banner.text", myFacetType.getPresentableName());
  }

  public JComponent createOptionsPanel() {
    return myFacetStructureConfigurable.getOrCreateFacetTypeEditor(myFacetType).createComponent();
  }

  public String getDisplayName() {
    return myFacetType.getPresentableName();
  }

  public Icon getIcon() {
    return myFacetType.getIcon();
  }

  public String getHelpTopic() {
    final FacetTypeEditor editor = myFacetStructureConfigurable.getFacetTypeEditor(myFacetType);
    return editor != null ? editor.getHelpTopic() : null;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

  public void updateComponent() {
    resetOptionsPanel();
  }
}
