import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * STICKMAN WAR - A Java Swing Game
 *
 * HOW TO COMPILE & RUN:
 *   javac StickmanWar.java
 *   java StickmanWar
 *
 * CONTROLS:
 *   [1] Spawn Swordsman  (30 gold)
 *   [2] Spawn Archer     (50 gold)
 *   [3] Spawn Giant      (100 gold)
 *   [4] Spawn Healer     (70 gold)
 *   [P] Pause / Resume
 *   [R] Restart
 */
public class StickmanWar extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StickmanWar());
    }

    public StickmanWar() {
        setTitle("Stickman War");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel();
        add(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        panel.startGame();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GamePanel — main canvas + game loop
// ─────────────────────────────────────────────────────────────────────────────
class GamePanel extends JPanel implements ActionListener {

    static final int W = 800, H = 450;
    static final int GROUND   = H - 70;
    static final int BLUE_BASE = 70;
    static final int RED_BASE  = W - 70;
    static final int FPS = 60;

    // Game state
    double gold = 500;
    boolean paused = false;
    boolean gameOver = false;
    String  gameResult = "";
    int  wave = 1;
    int  waveTimer = 0;
    int  waveInterval = 600;   // frames between enemy waves
    double goldAccum = 0;

    Castle blueCastle, redCastle;

    List<Unit>       units       = new CopyOnWriteArrayList<>();
    List<Projectile> projectiles = new CopyOnWriteArrayList<>();
    List<Particle>   particles   = new CopyOnWriteArrayList<>();

    javax.swing.Timer gameTimer;
    Random rng = new Random();

    // ── Colours ───────────────────────────────────────────────────────────────
    static final Color SKY_TOP   = new Color(0x1a2535);
    static final Color SKY_BOT   = new Color(0x253045);
    static final Color GROUND_C  = new Color(0x2d4a1e);
    static final Color DIRT_C    = new Color(0x3d2b1a);
    static final Color HILL1     = new Color(0x1e3a14);
    static final Color HILL2     = new Color(0x254a1a);
    static final Color COL_BLUE  = new Color(0x2155a3);
    static final Color COL_RED   = new Color(0xa32121);
    static final Color COL_GREEN = new Color(0x1a8a5a);
    static final Color COL_GOLD  = new Color(0xe67e22);

    // ── Button panel labels ───────────────────────────────────────────────────
    JLabel goldLabel, waveLabel, blueHpLabel, redHpLabel;

    GamePanel() {
        setPreferredSize(new Dimension(W, H + 80));
        setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        JPanel controls = buildControls();
        add(controls, BorderLayout.NORTH);

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                render((Graphics2D) g);
            }
        };
        canvas.setPreferredSize(new Dimension(W, H));
        canvas.setBackground(SKY_TOP);
        add(canvas, BorderLayout.CENTER);

        JPanel status = buildStatus();
        add(status, BorderLayout.SOUTH);

        // Key bindings
        setFocusable(true);
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case '1' -> spawnUnit("swordsman");
                    case '2' -> spawnUnit("archer");
                    case '3' -> spawnUnit("giant");
                    case '4' -> spawnUnit("healer");
                    case 'p', 'P' -> togglePause();
                    case 'r', 'R' -> restartGame();
                }
            }
        });
        canvas.requestFocusInWindow();

        // Store canvas ref for repaint
        this.canvas = canvas;
    }

    JPanel canvas;

    JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBackground(new Color(0x111122));

        JLabel lbl = new JLabel("YOUR NATION (Blue):");
        lbl.setForeground(new Color(0x5dade2));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        p.add(lbl);

        p.add(makeBtn("1 · Swordsman (30g)", COL_BLUE,  () -> spawnUnit("swordsman")));
        p.add(makeBtn("2 · Archer (50g)",    COL_BLUE,  () -> spawnUnit("archer")));
        p.add(makeBtn("3 · Giant (100g)",    COL_RED,   () -> spawnUnit("giant")));
        p.add(makeBtn("4 · Healer (70g)",    COL_GREEN, () -> spawnUnit("healer")));

        goldLabel = new JLabel("Gold: 150");
        goldLabel.setForeground(COL_GOLD);
        goldLabel.setFont(goldLabel.getFont().deriveFont(Font.BOLD, 13f));
        p.add(Box.createHorizontalStrut(20));
        p.add(goldLabel);

        JButton pauseBtn = new JButton("Pause [P]");
        pauseBtn.addActionListener(e -> togglePause());
        styleBtn(pauseBtn, Color.GRAY);
        p.add(pauseBtn);

        JButton restartBtn = new JButton("Restart [R]");
        restartBtn.addActionListener(e -> restartGame());
        styleBtn(restartBtn, Color.GRAY);
        p.add(restartBtn);

        return p;
    }

    JPanel buildStatus() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x111122));
        p.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        blueHpLabel = new JLabel("Blue Castle: 100 HP");
        blueHpLabel.setForeground(new Color(0x5dade2));
        blueHpLabel.setFont(blueHpLabel.getFont().deriveFont(11f));

        waveLabel = new JLabel("Wave 1  ·  Earn gold by defeating enemies", SwingConstants.CENTER);
        waveLabel.setForeground(new Color(0xaaaaaa));
        waveLabel.setFont(waveLabel.getFont().deriveFont(11f));

        redHpLabel = new JLabel("Red Castle: 100 HP", SwingConstants.RIGHT);
        redHpLabel.setForeground(new Color(0xe74c3c));
        redHpLabel.setFont(redHpLabel.getFont().deriveFont(11f));

        p.add(blueHpLabel, BorderLayout.WEST);
        p.add(waveLabel,   BorderLayout.CENTER);
        p.add(redHpLabel,  BorderLayout.EAST);
        return p;
    }

    JButton makeBtn(String text, Color accent, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        styleBtn(b, accent);
        return b;
    }

    void styleBtn(JButton b, Color accent) {
        b.setBackground(new Color(0x1a1a2e));
        b.setForeground(accent.brighter());
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(accent.darker(), 1));
        b.setFont(b.getFont().deriveFont(12f));
    }

    // ── Game lifecycle ─────────────────────────────────────────────────────────
    void startGame() {
        blueCastle = new Castle("blue", 100);
        redCastle  = new Castle("red",  100);
        spawnWave();
        gameTimer = new javax.swing.Timer(1000 / FPS, this);
        gameTimer.start();
    }

    void restartGame() {
        units.clear(); projectiles.clear(); particles.clear();
        gold = 150; paused = false; gameOver = false; gameResult = "";
        wave = 1; waveTimer = 0; waveInterval = 600; goldAccum = 0;
        blueCastle = new Castle("blue", 100);
        redCastle  = new Castle("red",  100);
        spawnWave();
    }

    void togglePause() { paused = !paused; }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!paused && !gameOver) tick();
        canvas.repaint();
    }

    void tick() {
        // Passive gold
        goldAccum += 0.1;
        if (goldAccum >= 1) { gold += 1; goldAccum = 0; }
        gold = Math.min(gold, 9999);

        // Wave timer
        waveTimer++;
        if (waveTimer >= waveInterval) {
            wave++;
            waveTimer = 0;
            waveInterval = Math.max(300, waveInterval - 25);
            spawnWave();
        }

        // Update
        new ArrayList<>(units).forEach(Unit::update);
        units.removeIf(u -> u.dead && u.staggerTime <= 0);

        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.update();
            if (p.done) projectiles.remove(i);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update();
            if (p.life <= 0) particles.remove(i);
        }

        // Check game over
        if (blueCastle.hp <= 0) { gameOver = true; gameResult = "Red Nation Wins!"; }
        if (redCastle.hp  <= 0) { gameOver = true; gameResult = "Blue Nation Wins!"; }

        // Update labels
        goldLabel.setText("Gold: " + (int) gold);
        blueHpLabel.setText("Blue Castle: " + (int) Math.max(0, blueCastle.hp) + " HP");
        redHpLabel.setText("Red Castle: "  + (int) Math.max(0, redCastle.hp)  + " HP");
        int blueCount = (int) units.stream().filter(u -> !u.dead && u.team.equals("blue")).count();
        int redCount  = (int) units.stream().filter(u -> !u.dead && u.team.equals("red")).count();
        waveLabel.setText("Wave " + wave + "  ·  Blue: " + blueCount + " units  vs  Red: " + redCount + " units");
    }

    // ── Spawning ───────────────────────────────────────────────────────────────
    void spawnUnit(String type) {
        int cost = unitDefs(type)[0];
        if (gold < cost) return;
        gold -= cost;
        double x = BLUE_BASE + 25 + rng.nextInt(15);
        units.add(new Unit("blue", type, x, this));
    }

    void spawnEnemyUnit(String type) {
        double x = RED_BASE - 25 - rng.nextInt(15);
        units.add(new Unit("red", type, x, this));
    }

    void spawnWave() {
        String[] pool = wave < 3
            ? new String[]{"swordsman","swordsman","archer"}
            : wave < 5
            ? new String[]{"swordsman","archer","giant","swordsman"}
            : new String[]{"swordsman","archer","giant","healer","swordsman"};

        int count = 2 + (int)(wave * 0.7);
        for (int i = 0; i < count; i++) {
            final int delay = i * 500;
            javax.swing.Timer t = new javax.swing.Timer(delay, e2 -> {
                spawnEnemyUnit(pool[rng.nextInt(pool.length)]);
                ((javax.swing.Timer) e2.getSource()).stop();
            });
            t.setRepeats(false);
            t.start();
        }
    }

    /**
     * Returns [cost, hp, maxHp, atkDmg, range, atkSpeed (frames), speed*100, reward, sizeX100]
     * speed and size stored *100 to keep int arrays.
     */
    static int[] unitDefs(String type) {
        return switch (type) {
            case "swordsman" -> new int[]{30,  60,  60,  8,  28,  60, 80,  15, 100};
            case "archer"    -> new int[]{50,  35,  35,  12, 160, 80, 60,  20, 100};
            case "giant"     -> new int[]{100, 180, 180, 20, 36,  90, 40,  40, 150};
            case "healer"    -> new int[]{70,  40,  40,  0,  70,  70, 65,  25, 100};
            default          -> new int[]{30,  60,  60,  8,  28,  60, 80,  15, 100};
        };
    }

    // ── Rendering ──────────────────────────────────────────────────────────────
    void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g);
        drawCastle(g, blueCastle, BLUE_BASE, "blue");
        drawCastle(g, redCastle,  RED_BASE,  "red");

        for (Unit u : units)             if (!u.dead) u.draw(g);
        for (Projectile p : projectiles) p.draw(g);
        for (Particle p : particles)     p.draw(g);

        if (gameOver) drawGameOver(g);
    }

    void drawBackground(Graphics2D g) {
        // Sky
        g.setColor(SKY_TOP);
        g.fillRect(0, 0, W, GROUND - 20);

        // Hills
        g.setColor(HILL2);
        g.fillOval(80, GROUND - 80, 240, 100);
        g.setColor(HILL1);
        g.fillOval(430, GROUND - 75, 280, 90);

        // Ground strip
        g.setColor(GROUND_C);
        g.fillRect(0, GROUND - 5, W, 25);

        // Dirt
        g.setColor(DIRT_C);
        g.fillRect(0, GROUND + 20, W, H - GROUND - 20);

        // Ground line
        g.setColor(new Color(0x3a6028));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, GROUND, W, GROUND);
        g.setStroke(new BasicStroke(1f));
    }

    void drawCastle(Graphics2D g, Castle castle, int cx, String team) {
        boolean isBlue = team.equals("blue");
        int bx = isBlue ? cx - 40 : cx - 10;

        Color mainCol  = isBlue ? new Color(0x1a4a8a) : new Color(0x8a1a1a);
        Color wallCol  = new Color(0x555555);
        Color merlCol  = new Color(0x666666);
        Color flagCol  = isBlue ? new Color(0x2155a3) : new Color(0xa32121);

        // Tower body
        g.setColor(mainCol);
        g.fillRect(bx, GROUND - 85, 50, 85);

        // Battlements
        g.setColor(merlCol);
        for (int i = 0; i < 4; i++) g.fillRect(bx + i * 14, GROUND - 97, 10, 14);

        // Gate arch
        g.setColor(new Color(0x111111));
        int gx = bx + (isBlue ? 30 : 8);
        g.fillArc(gx, GROUND - 28, 16, 20, 0, 180);
        g.fillRect(gx, GROUND - 20, 16, 22);

        // Windows
        g.setColor(new Color(0xffff88));
        g.fillRect(bx + 8,  GROUND - 68, 8, 10);
        g.fillRect(bx + 22, GROUND - 68, 8, 10);

        // Flagpole
        g.setColor(new Color(0xaaaaaa));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(bx + 25, GROUND - 97, bx + 25, GROUND - 120);
        g.setStroke(new BasicStroke(1f));

        // Flag
        g.setColor(flagCol);
        int[] fx = isBlue
            ? new int[]{bx+25, bx+41, bx+25}
            : new int[]{bx+25, bx+9,  bx+25};
        int[] fy = {GROUND - 120, GROUND - 112, GROUND - 104};
        g.fillPolygon(fx, fy, 3);

        // HP bar
        int bw = 60, bh = 7;
        int hbx = bx - 5, hby = GROUND - 112;
        g.setColor(new Color(0x440000));
        g.fillRect(hbx, hby, bw, bh);
        float pct = (float)(Math.max(0, castle.hp) / castle.maxHp);
        g.setColor(pct > 0.5f ? new Color(0x2ecc71) : pct > 0.25f ? new Color(0xf39c12) : new Color(0xe74c3c));
        g.fillRect(hbx, hby, (int)(bw * pct), bh);
        g.setColor(new Color(0x888888));
        g.drawRect(hbx, hby, bw, bh);

        // HP text
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString((int) Math.max(0, castle.hp) + " HP", hbx + 18, hby - 2);
    }

    void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, W, H);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(gameResult, (W - fm.stringWidth(gameResult)) / 2, H / 2 - 10);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        fm = g.getFontMetrics();
        String sub = "Press R to restart";
        g.drawString(sub, (W - fm.stringWidth(sub)) / 2, H / 2 + 20);
    }

    void addParticle(double x, double y, Color c, String text) {
        particles.add(new Particle(x, y, c, text));
    }

    void addDeathParticles(double x, double y) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 / 8 * i;
            particles.add(new Particle(x, y, new Color(0xe74c3c), null,
                Math.cos(angle) * 2.5, Math.sin(angle) * 2.5 - 1));
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Castle
// ─────────────────────────────────────────────────────────────────────────────
class Castle {
    String team;
    double hp, maxHp;
    Castle(String team, double hp) { this.team = team; this.hp = this.maxHp = hp; }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Unit
// ─────────────────────────────────────────────────────────────────────────────
class Unit {
    String team, type;
    double x, y;
    double hp, maxHp;
    int atk, range, atkSpeed, reward;
    double speed, size;
    int atkCd = 0, staggerTime = 0;
    boolean dead = false, moving = true;
    double animFrame;
    int dir;
    GamePanel game;

    Unit(String team, String type, double x, GamePanel game) {
        this.team = team;
        this.type = type;
        this.x    = x;
        this.y    = GamePanel.GROUND;
        this.game = game;
        this.dir  = team.equals("blue") ? 1 : -1;
        this.animFrame = Math.random() * Math.PI * 2;

        int[] d = GamePanel.unitDefs(type);
        // [cost, hp, maxHp, atkDmg, range, atkSpeed, speed*100, reward, size*100]
        this.hp       = d[1];
        this.maxHp    = d[2];
        this.atk      = d[3];
        this.range    = d[4];
        this.atkSpeed = d[5];
        this.speed    = d[6] / 100.0;
        this.reward   = d[7];
        this.size     = d[8] / 100.0;
    }

    void update() {
        if (dead) { if (staggerTime > 0) staggerTime--; return; }
        animFrame += 0.05;
        if (atkCd > 0) atkCd--;
        if (staggerTime > 0) staggerTime--;

        List<Unit> enemies = new ArrayList<>();
        for (Unit u : game.units) if (!u.dead && !u.team.equals(team)) enemies.add(u);

        Unit target = null;
        double minDist = Double.MAX_VALUE;
        for (Unit e : enemies) {
            double d = Math.abs(e.x - x);
            if (d < minDist) { minDist = d; target = e; }
        }

        // ── Healer logic ──────────────────────────────────────────────────────
        if (type.equals("healer")) {
            Unit healTarget = null;
            double lowestHp = Double.MAX_VALUE;
            for (Unit u : game.units) {
                if (!u.dead && u.team.equals(team) && u != this && u.hp < u.maxHp && u.hp < lowestHp) {
                    lowestHp = u.hp;
                    healTarget = u;
                }
            }
            if (healTarget != null) {
                double dist = Math.abs(healTarget.x - x);
                if (dist < range) {
                    moving = false;
                    if (atkCd <= 0) {
                        healTarget.hp = Math.min(healTarget.maxHp, healTarget.hp + 0.001);
                        atkCd = atkSpeed;
                        game.addParticle(healTarget.x, healTarget.y - 25, new Color(0x2ecc71), "+5");
                    }
                } else {
                    moving = true;
                    dir = healTarget.x > x ? 1 : -1;
                    x += speed * dir;
                }
            } else {
                moving = false;
            }
            return;
        }

        // ── Normal unit logic ─────────────────────────────────────────────────
        if (target != null) {
            double dist = Math.abs(target.x - x);
            dir = target.x > x ? 1 : -1;
            if (dist > range) {
                moving = true;
                x += speed * dir;
            } else {
                moving = false;
                if (atkCd <= 0) {
                    if (type.equals("archer")) {
                        game.projectiles.add(new Projectile(x, y - 14 * size, dir * 5, -1.5, team, atk, target));
                    } else {
                        target.hp -= atk;
                        target.staggerTime = 8;
                        game.addParticle(target.x, target.y - 15, new Color(0xe74c3c), "-" + atk);
                    }
                    atkCd = atkSpeed;
                }
            }
        } else {
            // March toward enemy base
            moving = true;
            int baseX = team.equals("blue") ? GamePanel.RED_BASE : GamePanel.BLUE_BASE;
            dir = baseX > x ? 1 : -1;
            x += speed * dir;

            // Attack castle when close
            boolean atBase = team.equals("blue") ? x > GamePanel.RED_BASE - 45
                                                 : x < GamePanel.BLUE_BASE + 45;
            if (atBase && atkCd <= 0) {
                Castle castle = team.equals("blue") ? game.redCastle : game.blueCastle;
                castle.hp -= atk * 0.5;
                atkCd = atkSpeed;
                int cx = team.equals("blue") ? GamePanel.RED_BASE - 20 : GamePanel.BLUE_BASE + 20;
                game.addParticle(cx, GamePanel.GROUND - 40, new Color(0xff5500), "!");
            }
        }

        // Clamp inside arena
        x = Math.max(GamePanel.BLUE_BASE + 10, Math.min(GamePanel.RED_BASE - 10, x));

        // Death
        if (hp <= 0) {
            dead = true;
            game.addDeathParticles(x, y - 15);
            if (team.equals("red")) game.gold += reward;
        }
    }

    void draw(Graphics2D g) {
        if (dead) return;
        Color col = switch (type) {
            case "giant"  -> team.equals("blue") ? new Color(0x1a6aaa) : new Color(0xaa2020);
            case "healer" -> team.equals("blue") ? new Color(0x1a8a5a) : new Color(0xaa6a1a);
            default       -> team.equals("blue") ? new Color(0x2155a3) : new Color(0xa32121);
        };
        drawStickman(g, (int) x, (int) y, col);
    }

    void drawStickman(Graphics2D g, int px, int py, Color col) {
        double sc = size;
        boolean walking = moving && !dead;
        double legSwing = walking ? Math.sin(animFrame * 3) * 0.35 : 0;
        double armSwing = walking ? Math.sin(animFrame * 3 + 0.5) * 0.3 : 0;

        g.setColor(col);
        g.setStroke(new BasicStroke((float)(1.5 * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Head
        int hr = (int)(6 * sc);
        int hcy = (int)(py - 22 * sc);
        if (type.equals("giant")) { g.fillOval(px - hr, hcy - hr, hr * 2, hr * 2); }
        else { g.drawOval(px - hr, hcy - hr, hr * 2, hr * 2); }

        // Body
        g.drawLine(px, (int)(py - 16 * sc), px, (int)(py - 6 * sc));

        // Arms
        int ax1 = (int)(px + 8 * sc * Math.cos(armSwing + 0.3) * dir);
        int ay1 = (int)(py - 14 * sc + 8 * sc * Math.sin(armSwing + 0.3));
        g.drawLine(px, (int)(py - 14 * sc), ax1, ay1);
        int ax2 = (int)(px - 7 * sc * Math.cos(-armSwing + 0.2) * dir);
        int ay2 = (int)(py - 14 * sc + 7 * sc * Math.sin(-armSwing + 0.2));
        g.drawLine(px, (int)(py - 14 * sc), ax2, ay2);

        // Legs
        int lx1 = (int)(px + 7 * sc * Math.sin(legSwing));
        int lx2 = (int)(px - 7 * sc * Math.sin(legSwing));
        g.drawLine(px, (int)(py - 6 * sc), lx1, py);
        g.drawLine(px, (int)(py - 6 * sc), lx2, py);

        // Weapon
        drawWeapon(g, px, py, sc, col);

        // HP bar
        int bw = (int)(28 * sc), bh = (int)(3 * sc);
        int bx = px - bw / 2, by = (int)(py - 36 * sc);
        g.setColor(new Color(0x440000));
        g.fillRect(bx, by, bw, bh);
        float pct = (float)(hp / maxHp);
        g.setColor(pct > 0.5f ? new Color(0x2ecc71) : pct > 0.25f ? new Color(0xf39c12) : new Color(0xe74c3c));
        g.fillRect(bx, by, (int)(bw * pct), bh);
    }

    void drawWeapon(Graphics2D g, int px, int py, double sc, Color col) {
        g.setStroke(new BasicStroke(1.5f));
        switch (type) {
            case "swordsman", "giant" -> {
                g.setColor(new Color(0xaaaaaa));
                int wx = (int)(px + 10 * sc * dir);
                int wy = (int)(py - 14 * sc);
                g.drawLine(wx, (int)(wy - 10 * sc), wx, (int)(wy + 4 * sc));
                g.drawLine((int)(wx - 4 * sc), wy, (int)(wx + 4 * sc), wy);
            }
            case "archer" -> {
                g.setColor(new Color(0x8B6914));
                int ax = (int)(px + 9 * sc * dir);
                int ay = (int)(py - 14 * sc);
                int r  = (int)(7 * sc);
                g.drawArc(ax - r, ay - r, r * 2, r * 2, dir > 0 ? 270 : 90, 180);
                g.setColor(new Color(0xaaaaaa));
                g.drawLine(ax, ay - r, ax, ay + r);
            }
            case "healer" -> {
                g.setColor(Color.WHITE);
                int hx = (int)(px + 8 * sc * dir);
                int hy = (int)(py - 14 * sc);
                int cr = (int)(5 * sc);
                g.drawLine(hx, hy - cr, hx, hy + cr);
                g.drawLine(hx - cr, hy, hx + cr, hy);
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Projectile (arrow)
// ─────────────────────────────────────────────────────────────────────────────
class Projectile {
    double x, y, vx, vy;
    String team;
    int dmg;
    Unit target;
    boolean done = false;

    Projectile(double x, double y, double vx, double vy, String team, int dmg, Unit target) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.team = team; this.dmg = dmg; this.target = target;
    }

    void update() {
        x += vx; y += vy; vy += 0.15;
        if (!target.dead && Math.abs(x - target.x) < 14 && Math.abs(y - (target.y - 14)) < 20) {
            target.hp -= dmg;
            target.staggerTime = 8;
            done = true;
        }
        if (y > GamePanel.H || x < 0 || x > GamePanel.W) done = true;
    }

    void draw(Graphics2D g) {
        g.setColor(new Color(0x8B6914));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) x, (int) y, (int)(x - vx * 4), (int)(y - vy * 4));
        // Arrowhead
        g.setColor(new Color(0xcccccc));
        g.fillOval((int) x - 2, (int) y - 2, 4, 4);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Particle
// ─────────────────────────────────────────────────────────────────────────────
class Particle {
    double x, y, vx, vy;
    Color color;
    String text;
    int life = 45;
    boolean dot;

    Particle(double x, double y, Color color, String text) {
        this.x = x; this.y = y; this.color = color; this.text = text;
        this.vx = 0; this.vy = -1.2;
    }

    Particle(double x, double y, Color color, String text, double vx, double vy) {
        this.x = x; this.y = y; this.color = color; this.text = text;
        this.vx = vx; this.vy = vy; this.dot = true;
    }

    void update() { x += vx; y += vy; life--; }

    void draw(Graphics2D g) {
        float alpha = Math.max(0f, life / 45f);
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        if (dot) {
            g.setColor(c);
            g.fillOval((int) x - 3, (int) y - 3, 6, 6);
        } else if (text != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(c);
            g.drawString(text, (int) x, (int) y);
        }
    }
}
