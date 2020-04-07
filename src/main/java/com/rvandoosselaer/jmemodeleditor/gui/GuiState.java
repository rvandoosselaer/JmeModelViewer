package com.rvandoosselaer.jmemodeleditor.gui;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.rvandoosselaer.jmemodeleditor.EditorState;
import com.rvandoosselaer.jmemodeleditor.Main;
import com.rvandoosselaer.jmeutils.gui.GuiTranslations;
import com.rvandoosselaer.jmeutils.gui.GuiUtils;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.style.ElementId;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The state that manages all GUI elements and user interactions.
 *
 * @author: rvandoosselaer
 */
public class GuiState extends BaseAppState {

    public static final String STYLE = "editor-style";
    public static final String DARK_STYLE_RESOURCE = "dark-style.groovy";

    private static final String BOOKMARK_KEY_FORMAT = "bookmark.%d";
    private static final String RECENT_KEY_FORMAT = "recent.%d";

    @Getter
    private Node guiNode;
    private Container toolbar;
    private OpenFileWindow openFileWindow;
    private PropertiesPanel propertiesPanel;
    private float zIndex = 99;
    private EditorState editorState;
    private TooltipState tooltipState;
    private Label fpsLabel;
    private VersionedHolder<Integer> fps = new VersionedHolder<>(0);
    private VersionedReference<Integer> fpsRef = fps.createReference();
    private int frameCounter;
    private float timeCounter;

    @Override
    protected void initialize(Application app) {
        editorState = getState(EditorState.class);
        tooltipState = getState(TooltipState.class);
        guiNode = ((SimpleApplication) app).getGuiNode();

        toolbar = createToolbar();
        fpsLabel = createFpsLabel();
        openFileWindow = createOpenFileWindow();
        propertiesPanel = createPropertiesPanel();
    }

    @Override
    protected void cleanup(Application app) {
        openFileWindow.cleanup();
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(toolbar);
        guiNode.attachChild(fpsLabel);
        guiNode.attachChild(propertiesPanel);
    }

    @Override
    protected void onDisable() {
        toolbar.removeFromParent();
        fpsLabel.removeFromParent();
        propertiesPanel.removeFromParent();
        if (openFileWindow.getParent() != null) {
            openFileWindow.removeFromParent();
        }
    }

    @Override
    public void update(float tpf) {
        calculateFps(tpf);
        if (fpsRef.update()) {
            fpsLabel.setText(String.format("%d", fpsRef.get()));
        }
        refreshLayout();
    }

    public void loadModel(Path path) {
        Spatial model = editorState.loadModel(path);
        propertiesPanel.setModel(model);
    }

    /**
     * @return list of recent locations
     */
    public List<Path> getRecentFolders() {
        List<Path> recentLocations = new ArrayList<>();

        int i = 0;
        while (getPreferences().get(String.format(RECENT_KEY_FORMAT, i), null) != null) {
            String pathString = getPreferences().get(String.format(RECENT_KEY_FORMAT, i), null);
            recentLocations.add(Paths.get(pathString));
            i++;
        }

        return recentLocations;
    }

    public void addRecentLocation(Path path) {
        if (path == null) {
            return;
        }

        List<Path> recentLocations = getRecentFolders();

        clearRecentLocations();

        // if the path was already in the list, remove it and add it at the top
        recentLocations.remove(path);
        recentLocations.add(0, path);

        for (int i = 0; i < recentLocations.size(); i++) {
            getPreferences().put(String.format(RECENT_KEY_FORMAT, i), recentLocations.get(i).toString());
        }

    }

    public void clearRecentLocations() {
        int i = 0;
        while (getPreferences().get(String.format(RECENT_KEY_FORMAT, i), null) != null) {
            getPreferences().remove(String.format(RECENT_KEY_FORMAT, i));
            i++;
        }
    }

    /**
     * @return list of bookmark locations
     */
    public List<Path> getBookmarks() {
        List<Path> bookmarks = new ArrayList<>();

        Preferences prefs = getPreferences();
        int i = 0;
        while (prefs.get(String.format(BOOKMARK_KEY_FORMAT, i), null) != null) {
            String pathString = prefs.get(String.format(BOOKMARK_KEY_FORMAT, i), null);
            bookmarks.add(Paths.get(pathString));
            i++;
        }

        return bookmarks;
    }

    public void addBookmark(Path path) {
        if (path == null) {
            return;
        }

        List<Path> bookmarks = getBookmarks();
        String pathString = Files.isDirectory(path) ? path.toAbsolutePath().toString() : path.toAbsolutePath().getParent().toString();

        getPreferences().put(String.format(BOOKMARK_KEY_FORMAT, bookmarks.size()), pathString);
    }

    public void removeBookmark(Path path) {
        if (path == null) {
            return;
        }

        List<Path> bookmarks = getBookmarks();
        for (int i = 0; i < bookmarks.size(); i++) {
            getPreferences().remove(String.format(BOOKMARK_KEY_FORMAT, i));
        }

        bookmarks.remove(path);

        for (int i = 0; i < bookmarks.size(); i++) {
            getPreferences().put(String.format(BOOKMARK_KEY_FORMAT, i), bookmarks.get(i).toString());
        }
    }

    private void onOpenFile() {
        if (!openFileWindow.isAttached()) {
            // the Lemur ListBox has soms 'quirks'. It needs to be rendered before it's size is correctly calculated.
            // that's why we layout the modal 1 frame after it's attached
            getApplication().enqueue(() -> layoutOpenFileWindow(openFileWindow, GuiUtils.getWidth(), GuiUtils.getHeight()));
            guiNode.attachChild(openFileWindow);
        }
    }

    private void onReload() {
        boolean reloaded = editorState.reloadModel();
        if (reloaded) {
            propertiesPanel.setModel(editorState.getModel());
        }
    }

    private void onSave() {
        editorState.saveModel();
    }

    private void refreshLayout() {
        int w = GuiUtils.getWidth();
        int h = GuiUtils.getHeight();

        layoutToolbar(toolbar, w, h);
        layoutFpsLabel(fpsLabel, w, h);
        layoutPropertiesPanel(propertiesPanel, w, h);
    }

    private void layoutToolbar(Container toolbar, int width, int height) {
        toolbar.setPreferredSize(toolbar.getPreferredSize().setX(width));
        toolbar.setLocalTranslation(0, height, zIndex);
    }

    private void layoutFpsLabel(Label label, int width, int height) {
        label.setLocalTranslation(width - label.getPreferredSize().x, height, zIndex + 1);
    }

    private void layoutPropertiesPanel(PropertiesPanel propertiesPanel, int width, int height) {
        propertiesPanel.setPreferredSize(propertiesPanel.getPreferredSize().setX(width * 0.25f).setY(height - toolbar.getPreferredSize().y));
        propertiesPanel.setLocalTranslation(width * 0.75f, height - toolbar.getPreferredSize().y, zIndex);
    }

    private void layoutOpenFileWindow(OpenFileWindow window, int width, int height) {
        window.setPreferredSize(window.getPreferredSize().setX(width * 0.8f));
        GuiUtils.center(window);
        window.move(0, 0, zIndex + 10);
    }

    private void calculateFps(float tpf) {
        timeCounter += tpf;
        frameCounter++;
        if (timeCounter >= 1.0f) {
            fps.setObject((int) (frameCounter / timeCounter));
            timeCounter = 0;
            frameCounter = 0;
        }
    }

    private PropertiesPanel createPropertiesPanel() {
        PropertiesPanel propertiesPanel = new PropertiesPanel();
        return propertiesPanel;
    }

    private Label createFpsLabel() {
        return new Label("0");
    }

    private Container createToolbar() {
        Container container = new Container(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None), new ElementId("toolbar"));

        Button open = container.addChild(createToolbarButton("/Interface/open.png"));
        open.addClickCommands(source -> onOpenFile());
        tooltipState.addTooltip(open, GuiTranslations.getInstance().t("toolbar.open.tooltip"));

        Button save = container.addChild(createToolbarButton("/Interface/save.png"));
        save.addClickCommands(source -> onSave());
        tooltipState.addTooltip(save, GuiTranslations.getInstance().t("toolbar.save.tooltip"));

        Button reload = container.addChild(createToolbarButton("/Interface/reload.png"));
        reload.addClickCommands(source -> onReload());
        tooltipState.addTooltip(reload, GuiTranslations.getInstance().t("toolbar.reload.tooltip"));

        return container;
    }

    private OpenFileWindow createOpenFileWindow() {
        return new OpenFileWindow();
    }

    private static Button createToolbarButton(String iconPath) {
        Button button = new Button("", new ElementId("toolbar").child("button"));
        IconComponent icon = new IconComponent(iconPath);
        icon.setIconSize(new Vector2f(16, 16));
        icon.setMargin(2, 2);
        icon.setHAlignment(HAlignment.Center);
        icon.setVAlignment(VAlignment.Center);
        button.setIcon(icon);

        return button;
    }

    private static Preferences getPreferences() {
        return Preferences.userNodeForPackage(Main.class);
    }

}
