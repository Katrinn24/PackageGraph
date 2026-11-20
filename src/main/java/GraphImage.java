import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class GraphImage {
    private static final int MARGIN = 36;
    private static final int HEADER = 100;
    private static final int NODE_WIDTH = 198;
    private static final int NODE_HEIGHT = 42;
    private static final int COLUMN = 265;
    private static final int ROW = 68;
    private static final long MAX_PIXELS = 24_000_000;
    private static final Color INK = new Color(28, 42, 62);
    private static final Color EDGE = new Color(78, 103, 135);
    private static final Color ROOT = new Color(32, 79, 157);

    private GraphImage() {
    }

    static BufferedImage draw(
            Map<String, List<String>> graph, String root, Function<String, String> label) {
        Map<String, Box> boxes = placeNodes(graph, root);
        int width = Math.max(420, maxCoordinate(boxes, Box::x) + NODE_WIDTH + MARGIN);
        int height = Math.max(HEADER, maxCoordinate(boxes, Box::y)) + NODE_HEIGHT + MARGIN;
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Граф слишком велик для PNG. Уменьши maxDepth.");
        }

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setColor(Color.WHITE);
            canvas.fillRect(0, 0, width, height);
            canvas.setColor(INK);
            canvas.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            String title = boxes.isEmpty() ? "Нет пакетов после фильтрации" : label.apply(root);
            canvas.drawString(fitText(canvas, title, width - MARGIN * 2), MARGIN, MARGIN + 6);
            graph.forEach((name, links) -> links.forEach(link ->
                    drawEdge(canvas, boxes.get(name), boxes.get(link))));
            boxes.forEach((name, box) -> drawNode(canvas, box, label.apply(name), name.equals(root)));
        } finally {
            canvas.dispose();
        }
        return image;
    }

    private static Map<String, Box> placeNodes(Map<String, List<String>> graph, String root) {
        Map<String, Box> boxes = new LinkedHashMap<>();
        if (graph.isEmpty()) {
            return boxes;
        }

        var queue = new ArrayDeque<String>();
        Map<String, Integer> columns = new HashMap<>();
        Map<Integer, Integer> rows = new HashMap<>();
        queue.add(root);
        columns.put(root, 0);
        while (!queue.isEmpty()) {
            String name = queue.remove();
            int column = columns.get(name);
            int row = rows.merge(column, 1, Integer::sum) - 1;
            boxes.put(name, new Box(MARGIN + column * COLUMN, HEADER + row * ROW));
            for (String link : graph.get(name)) {
                if (columns.putIfAbsent(link, column + 1) == null) {
                    queue.add(link);
                }
            }
        }
        return boxes;
    }

    private static int maxCoordinate(Map<String, Box> boxes, java.util.function.ToIntFunction<Box> axis) {
        return boxes.values().stream().mapToInt(axis).max().orElse(0);
    }

    private static void drawEdge(Graphics2D canvas, Box from, Box to) {
        canvas.setColor(EDGE);
        canvas.setStroke(new BasicStroke(1.4f));
        if (from.x() < to.x()) {
            int startX = from.x() + NODE_WIDTH;
            int startY = from.y() + NODE_HEIGHT / 2;
            int endX = to.x();
            int endY = to.y() + NODE_HEIGHT / 2;
            canvas.drawLine(startX, startY, endX, endY);
            drawArrow(canvas, startX, startY, endX, endY);
            return;
        }
        if (from.x() == to.x() && from.y() != to.y()) {
            drawColumnEdge(canvas, from, to);
            return;
        }

        int startX = from.x() + NODE_WIDTH * 3 / 4;
        int endX = to.x() + NODE_WIDTH / 4;
        int controlX = (startX + endX) / 2;
        int controlY = Math.min(from.y(), to.y()) - ROW / 2;
        var curve = new Path2D.Double();
        curve.moveTo(startX, from.y());
        curve.quadTo(controlX, controlY, endX, to.y());
        canvas.draw(curve);
        drawArrow(canvas, controlX, controlY, endX, to.y());
    }

    private static void drawColumnEdge(Graphics2D canvas, Box from, Box to) {
        boolean downward = from.y() < to.y();
        int x = from.x() + (downward ? NODE_WIDTH : 0);
        int lane = x + (downward ? MARGIN : -MARGIN / 2);
        int startY = from.y() + NODE_HEIGHT / 2;
        int endY = to.y() + NODE_HEIGHT / 2;
        var edge = new Path2D.Double();
        edge.moveTo(x, startY);
        edge.curveTo(lane, startY, lane, endY, x, endY);
        canvas.draw(edge);
        drawArrow(canvas, lane, endY, x, endY);
    }

    private static void drawArrow(Graphics2D canvas, int fromX, int fromY, int x, int y) {
        double angle = Math.atan2((double) y - fromY, (double) x - fromX);
        var arrow = new Path2D.Double();
        arrow.moveTo(x, y);
        arrow.lineTo(x - 9 * Math.cos(angle - 0.46), y - 9 * Math.sin(angle - 0.46));
        arrow.lineTo(x - 9 * Math.cos(angle + 0.46), y - 9 * Math.sin(angle + 0.46));
        arrow.closePath();
        canvas.fill(arrow);
    }

    private static void drawNode(Graphics2D canvas, Box box, String name, boolean root) {
        canvas.setColor(root ? ROOT : new Color(233, 239, 248));
        canvas.fillRoundRect(box.x(), box.y(), NODE_WIDTH, NODE_HEIGHT, 12, 12);
        canvas.setColor(root ? Color.WHITE : INK);
        canvas.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        String display = fitText(canvas, name, NODE_WIDTH - 22);
        int left = box.x() + (NODE_WIDTH - canvas.getFontMetrics().stringWidth(display)) / 2;
        canvas.drawString(display, left, box.y() + NODE_HEIGHT / 2 + 5);
    }

    private static String fitText(Graphics2D canvas, String name, int width) {
        var metrics = canvas.getFontMetrics();
        if (metrics.stringWidth(name) <= width) {
            return name;
        }
        int end = name.length();
        while (metrics.stringWidth(name.substring(0, end) + "…") > width) {
            end--;
        }
        return name.substring(0, end) + "…";
    }

    private record Box(int x, int y) {
    }
}
