package com.jme3.tmx.core;

import java.util.List;

import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.texture.Texture;
import com.jme3.tmx.math2d.Rectangle;
import com.jme3.tmx.math2d.Shape;

/**
 * An object occupying an {@link com.jme3.tmx.core.ObjectLayer}.
 * 
 * While tile layers are very suitable for anything repetitive aligned to the
 * tile grid, sometimes you want to annotate your map with other information,
 * not necessarily aligned to the grid. Hence the objects have their coordinates
 * and size in pixels, but you can still easily align that to the grid when you
 * want to.
 * 
 * You generally use objects to add custom information to your tile map, such as
 * spawn points, warps, exits, etc.
 * 
 * When the object has a gid set, then it is represented by the image of the
 * tile with that global ID. The image alignment currently depends on the map
 * orientation. In orthogonal orientation it's aligned to the bottom-left while
 * in isometric it's aligned to the bottom-center.
 * 
 * Can contain: properties, ellipse (since 0.9), polygon, polyline, image
 * 
 * @author yanmaoyuan
 * 
 */
public class ObjectNode extends Base {

	public enum ObjectGroupType {
		/**
		 * No need to explain.
		 */
		Rectangle,
		/**
		 * Used to mark an object as an ellipse. The existing x, y, width and
		 * height attributes are used to determine the size of the ellipse.
		 */
		Ellipse,
		/**
		 * <points>: A list of x,y coordinates in pixels.
		 * 
		 * Each polygon object is made up of a space-delimited list of x,y
		 * coordinates. The origin for these coordinates is the location of the
		 * parent object. By default, the first point is created as 0,0 denoting
		 * that the point will originate exactly where the object is placed.
		 */
		Polygon,
		/**
		 * A polyline follows the same placement definition as a polygon object.
		 */
		Polyline,
		/**
		 * An tile references to a tile with it's gid.
		 */
		Tile,
		/**
		 * An image
		 */
		Image;
	}

	private ObjectLayer objectGroup;

	/**
	 * Unique ID of the object. Each object that is placed on a map gets a
	 * unique id. Even if an object was deleted, no object gets the same ID. Can
	 * not be changed in Tiled Qt. (since Tiled 0.11)
	 */
	private int id;

	/**
	 * The name of the object. An arbitrary string.
	 */
	private String name;

	/**
	 * The type of the object. An arbitrary string.
	 */
	private String type;
	private ObjectGroupType objectGroupType;
	private Shape shape = new Rectangle();

	/**
	 * The (x, y) coordinate of the object in pixels.
	 */
	private double x, y;

	/**
	 * The width and height of the object in pixels (defaults to 0).
	 */
	private double width = 0, height = 0;

	/**
	 * The rotation of the object in degrees clockwise (defaults to 0). (since
	 * 0.10)
	 */
	private float rotation = 0f;

	/**
	 * Whether the object is shown (1) or hidden (0). Defaults to 1. (since 0.9)
	 */
	private boolean visible;

	/**
	 * An reference to a tile (optional). 
	 * 
	 * When the object has a gid set, then it is represented by the image of the tile with that global ID. The image alignment currently depends on the map orientation. In orthogonal orientation it's aligned to the bottom-left while in isometric it's aligned to the bottom-center.
	 */
	private int gid;
	private Tile tile;// when ObjectGroupType == Tile

	// ObjectGroupType == Polygon || ObjectGroupType == Polyline
	private List<Vector2f> points;

	// ObjectGroupType == Image
	private String imageSource = "";
	private Texture texture;
	private Material material;

	/**
	 * Default constructor
	 */
	public ObjectNode() {
	}

	public ObjectNode(double x, double y, double width, double height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public ObjectLayer getObjectGroup() {
		return objectGroup;
	}

	public void setObjectGroup(ObjectLayer objectGroup) {
		this.objectGroup = objectGroup;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public ObjectGroupType getObjectGroupType() {
		return objectGroupType;
	}

	public void setObjectGroupType(ObjectGroupType objectGroupType) {
		this.objectGroupType = objectGroupType;
	}

	public Shape getShape() {
		return shape;
	}

	public void setShape(Shape shape) {
		this.shape = shape;
	}

	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public int getGid() {
		return gid;
	}

	public void setGid(int gid) {
		this.gid = gid;
	}

	public Tile getTile() {
		return tile;
	}

	public void setTile(Tile tile) {
		this.tile = tile;
	}

	public List<Vector2f> getPoints() {
		return points;
	}

	public void setPoints(List<Vector2f> points) {
		this.points = points;
	}

	public String getImageSource() {
		return imageSource;
	}

	public void setImageSource(String imageSource) {
		this.imageSource = imageSource;
	}

	public Texture getTexture() {
		return texture;
	}

	public void setTexture(Texture texture) {
		this.texture = texture;
	}

	public Material getMaterial() {
		return material;
	}

	public void setMaterial(Material material) {
		this.material = material;
	}
	
}