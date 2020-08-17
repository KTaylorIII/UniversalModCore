package cam72cam.mod.automate;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Playbook extends JScrollPane {
    public final File file;

    private final List<Action> actions = new ArrayList<>();
    JPanel pane;
    private int actionIdx = 0;
    private boolean runStep = false;
    private boolean runAll = false;

    private final GridBagConstraints c;
    private final String originalContents;

    public Playbook(File file) throws IOException {
        super();
        pane = new JPanel(new GridBagLayout());
        setViewportView(pane);
        setName(file.getName());

        c = new GridBagConstraints();

        this.file = file;
        if (file.exists()) {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                actions.add(Action.deserialize(line));
            }
            originalContents = String.join(System.lineSeparator(), lines);
        } else {
            actions.add(new GuiClickButton("Singleplayer"));
            actions.add(new GuiClickButton("Create New World"));
            actions.add(new GuiClickButton("Game Mode: Survival"));
            actions.add(new GuiClickButton("Game Mode: Hardcore"));
            actions.add(new GuiClickButton("More World Options..."));
            actions.add(new GuiClickButton("Generate Structures: ON"));
            actions.add(new GuiClickButton("World Type: Default"));
            actions.add(new GuiSetText("seed", "let it grow"));
            actions.add(new GuiClickButton("Create New World"));
            //actions.add(new GuiButtonClick("More World Options..."));
            originalContents = "";
        }

        this.redraw();
    }

    public void startover() {
        actionIdx = 0;
        runStep = false;
        runAll = true;
        redraw();
    }
    public void runStep() {
        runStep = true;
        redraw();
    }
    public void runAll() {
        runAll = true;
        redraw();
    }
    public void pause() {
        runStep = false;
        runAll = false;
        redraw();
    }
    public void stop() {
        runStep = false;
        runAll = false;
        actionIdx = 0;
        redraw();
    }
    public void save() throws IOException {
        saveAs(this.file);
    }

    public void saveAs(File file) throws IOException {
        Files.write(file.toPath(), saveContents().getBytes());
    }

    private String saveContents() {
        return actions.stream().map(Action::serialize).collect(Collectors.joining(System.lineSeparator()));
    }

    public boolean isModified() {
        return !saveContents().equals(originalContents);
    }

    public void tick() {
        if (runAll || runStep) {
            if (actionIdx == actions.size()) {
                stop();
                return;
            }

            if (!actions.isEmpty()) {
                if (actions.get(actionIdx).tick()) {
                    actionIdx++;
                    runStep = false;
                    redraw();
                }
            }
        }
    }

    private void redraw() {
        pane.removeAll();

        pane.addMouseListener(new MouseAdapter() {
            private int start;
            /*
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (actionIdx == finalI) {
                    runStep = true;
                } else {
                    actionIdx = finalI;
                    redraw();
                }
            }*/

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
                    this.start = Arrays.asList(pane.getComponents()).indexOf(pane.getComponentAt(mouseEvent.getPoint()));
                    pane.setCursor(new Cursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                int dest = Arrays.asList(pane.getComponents()).indexOf(pane.getComponentAt(mouseEvent.getPoint()));
                if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
                    pane.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    if (start >= 0) {
                        if (dest / 2 != start / 2) {
                            Action got = actions.remove(start / 2);
                            actions.add(dest / 2, got);
                            start = -1;
                            redraw();
                        }
                    }
                }
                if (SwingUtilities.isRightMouseButton(mouseEvent)) {
                    if (actionIdx != dest/2 && dest >= 0) {
                        System.out.println(actionIdx);
                        actionIdx = dest/2;
                        redraw();
                    }
                }
            }
        });

        for (int i = 0; i < actions.size(); i++) {
            JPanel sub = new JPanel(new FlowLayout(FlowLayout.LEFT));

            Action action = actions.get(i);
            JLabel l = new JLabel(i + "");
            l.setHorizontalAlignment(JLabel.CENTER);
            if (i == actionIdx) {
                sub.setBackground(runStep || runAll ? Color.GREEN : Color.YELLOW);
            }

            action.renderEditor(sub);
            sub.revalidate();

            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0;
            c.gridy = i;
            c.gridwidth = 1;
            pane.add(l, c);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 1;
            c.gridy = i;
            c.gridwidth = 5;
            pane.add(sub, c);
        }
        pane.revalidate();
    }

    public void removeCurrentAction() {
        this.actions.remove(this.actionIdx);
        redraw();
    }

    public void appendAction(Action action) {
        this.actions.add(action);
        redraw();
    }

    public void insertAction(Action action) {
        this.actions.add(actionIdx+1, action);
        redraw();
    }
}
