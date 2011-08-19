package playground.sergioo.NetworksMatcher.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import playground.sergioo.Visualizer2D.Camera;
import playground.sergioo.Visualizer2D.LayersWindow;
import playground.sergioo.Visualizer2D.NetworkVisualizer.NetworkPainters.NetworkPainter;

public class DoubleNetworkWindow extends LayersWindow implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//Enumerations
	private enum PanelIds implements LayersWindow.PanelId {
		A,
		B,
		ACTIVE,
		DOUBLE;
	}
	public enum Option implements LayersWindow.Option {
		SELECT_LINK("<html>L<br/>I<br/>N<br/>K</html>"),
		SELECT_NODE("<html>N<br/>O<br/>D<br/>E</html>"),
		SELECT_ZONE("<html>Z<br/>O<br/>N<br/>E</html>"),
		ZOOM("<html>Z<br/>O<br/>O<br/>M</html>");
		private String caption;
		private Option(String caption) {
			this.caption = caption;
		}
		@Override
		public String getCaption() {
			return caption;
		}
	}
	public enum Label implements LayersWindow.Label {
		LINK("Link"),
		NODE("Node");
		private String text;
		private Label(String text) {
			this.text = text;
		}
		@Override
		public String getText() {
			return text;
		}
	}
	
	//Attributes
	private JButton readyButton;
	private boolean networksSeparated = true;
	private JPanel panelsPanel;
	
	//Methods
	public DoubleNetworkWindow(String title, NetworkPainter networkPainterA, NetworkPainter networkPainterB) {
		setTitle(title);
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		this.setLocation(0,0);
		this.setLayout(new BorderLayout());
		panels.put(PanelIds.A, new NetworkPanel(this, networkPainterA));
		panels.put(PanelIds.B, new NetworkPanel(this, networkPainterB));
		panels.put(PanelIds.ACTIVE, panels.get(PanelIds.A));
		panels.put(PanelIds.DOUBLE, new DoubleNetworkPanel(this, networkPainterA, networkPainterB));
		panelsPanel = new JPanel();
		panelsPanel.setLayout(new GridLayout(1,2));
		panelsPanel.add(panels.get(PanelIds.A), BorderLayout.WEST);
		panelsPanel.add(panels.get(PanelIds.B), BorderLayout.EAST);
		this.add(panelsPanel, BorderLayout.CENTER);
		option = Option.ZOOM;
		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new GridLayout(Option.values().length,1));
		for(Option option:Option.values()) {
			JButton optionButton = new JButton(option.caption);
			optionButton.setActionCommand(option.getCaption());
			optionButton.addActionListener(this);
			buttonsPanel.add(optionButton);
		}
		this.add(buttonsPanel, BorderLayout.EAST);
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BorderLayout());
		readyButton = new JButton("Ready to exit");
		readyButton.addActionListener(this);
		readyButton.setActionCommand(READY_TO_EXIT);
		infoPanel.add(readyButton, BorderLayout.WEST);
		JPanel labelsPanel = new JPanel();
		labelsPanel.setLayout(new GridLayout(1,Label.values().length));
		labelsPanel.setBorder(new TitledBorder("Information"));
		labels = new JLabel[Label.values().length];
		labels[0]=new JLabel("");
		labelsPanel.add(labels[0]);
		labels[1]=new JLabel("");
		labelsPanel.add(labels[1]);
		infoPanel.add(labelsPanel, BorderLayout.CENTER);JPanel coordsPanel = new JPanel();
		coordsPanel.setLayout(new GridLayout(1,2));
		coordsPanel.setBorder(new TitledBorder("Coordinates"));
		coordsPanel.add(lblCoords[0]);
		coordsPanel.add(lblCoords[1]);
		infoPanel.add(coordsPanel, BorderLayout.EAST);
		this.add(infoPanel, BorderLayout.SOUTH);
		setSize(Toolkit.getDefaultToolkit().getScreenSize().width,Toolkit.getDefaultToolkit().getScreenSize().height);
	}
	public void setNetworksSeparated() {
		networksSeparated = !networksSeparated;
		if(networksSeparated) {
			this.remove(panels.get(PanelIds.DOUBLE));
			panels.put(PanelIds.ACTIVE, panels.get(PanelIds.A));
			panelsPanel = new JPanel();
			panelsPanel.setLayout(new GridLayout(1,2));
			panelsPanel.add(panels.get(PanelIds.A), BorderLayout.WEST);
			panelsPanel.add(panels.get(PanelIds.B), BorderLayout.EAST);
			this.add(panelsPanel, BorderLayout.CENTER);
		}
		else {
			this.remove(panelsPanel);
			this.add(panels.get(PanelIds.DOUBLE), BorderLayout.CENTER);
		}
	}
	public void cameraChange(Camera camera) {
		if(networksSeparated) {
			if(panels.get(PanelIds.ACTIVE)==panels.get(PanelIds.A)) {
				panels.get(PanelIds.B).getCamera().setCamera(camera.getUpLeftCorner(), camera.getSize());
				panels.get(PanelIds.B).repaint();
			}
			else {
				panels.get(PanelIds.A).getCamera().setCamera(camera.getUpLeftCorner(), camera.getSize());
				panels.get(PanelIds.A).repaint();
			}
		}
	}
	public void setActivePanel(NetworkPanel panel) {
		panels.put(PanelIds.ACTIVE, panel);
	}
	public void refreshLabel(Label label) {
		labels[label.ordinal()].setText(((NetworkPanel)panels.get(PanelIds.ACTIVE)).getLabelText(label));
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		for(Option option:Option.values())
			if(e.getActionCommand().equals(option.getCaption()))
				this.option = option;
		if(e.getActionCommand().equals(READY_TO_EXIT)) {
			setVisible(false);
			readyToExit = true;
		}
	}
	
}
