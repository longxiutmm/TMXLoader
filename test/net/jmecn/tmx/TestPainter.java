package net.jmecn.tmx;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.tmx.core.ObjectNode;
import com.jme3.tmx.core.ObjectNode.ObjectType;
import com.jme3.tmx.util.ObjectTexture;

public class TestPainter extends SimpleApplication {

	@Override
	public void simpleInitApp() {

		ObjectNode obj = new ObjectNode(0, 0, 20, 30);
		obj.setObjectType(ObjectType.Ellipse);
		
		ObjectTexture tex = new ObjectTexture(obj);
		obj.setTexture(tex);
		
		Material mat = new Material(assetManager, "com/jme3/tmx/render/Tiled.j3md");
		mat.setTexture("ColorMap", tex);
		mat.setColor("Color", ColorRGBA.Red);
		obj.setMaterial(mat);
		
		viewPort.setBackgroundColor(ColorRGBA.DarkGray);
		rootNode.attachChild(obj.getGeometry());
	}

	public static void main(String[] args) {
		TestPainter app = new TestPainter();
		app.setSettings(new AppSettings(true));
		app.start();
	}

}
