package com.rvandoosselaer.jmemodeleditor;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.rvandoosselaer.jmemodeleditor.gui.GuiState;
import com.rvandoosselaer.jmeutils.ApplicationGlobals;
import com.rvandoosselaer.jmeutils.Resolution;
import com.rvandoosselaer.jmeutils.ResolutionHelper;
import com.rvandoosselaer.jmeutils.util.LogUtils;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;
import lombok.extern.slf4j.Slf4j;

/**
 * @author: rvandoosselaer
 */
@Slf4j
public class Main extends SimpleApplication {

    public static void main(String[] args) {
        LogUtils.forwardJULToSlf4j();

        Main main = new Main();
        main.start();
    }

    public Main() {
        super(new GuiState(),
                new EditorState());

        setSettings(createSettings());

        setShowSettings(false);
    }

    @Override
    public void simpleInitApp() {
        GuiGlobals.initialize(this);
        ApplicationGlobals.initialize(this);
        getInputManager().deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);

        BaseStyles.loadStyleResources(GuiState.DARK_STYLE_RESOURCE);
        GuiGlobals.getInstance().getStyles().setDefaultStyle(GuiState.STYLE);
    }

    private AppSettings createSettings() {
        AppSettings settings = new AppSettings(true);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setGammaCorrection(true);
        settings.setResizable(true);
        settings.setSamples(4);
        settings.setTitle("JmeModelEditor");
        Resolution resolution = ResolutionHelper.getFirstHDResolution();
        settings.setResolution(resolution.getWidth(), resolution.getHeight());
        settings.setBitsPerPixel(resolution.getBpp());

        log.debug("Settings: {}", settings);

        return settings;
    }

}