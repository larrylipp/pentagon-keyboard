package com.pentagonkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PentagonKeyboardView extends View {

    // ── Callback ──────────────────────────────────────────────────────
    public interface KeyListener {
        void onKeyPress(String key);
        void onDelete();
        void onSpace();
        void onShiftToggle();
        void onModeToggle(String mode);
        void onSwipeWord(List<String> suggestions, String raw);
    }
    private KeyListener listener;
    public void setKeyListener(KeyListener l) { this.listener = l; }

    // ── Mode / shift ──────────────────────────────────────────────────
    private String mode = "alpha";
    private boolean shifted = false;
    public void setMode(String m)     { mode = m;    invalidate(); }
    public void setShifted(boolean s) { shifted = s; invalidate(); }
    public String  getMode()    { return mode; }
    public boolean isShifted()  { return shifted; }

    // ── Hand mode ─────────────────────────────────────────────────────
    // "left" = pentagon left, side panel right (default)
    // "right" = pentagon right, side panel left (mirrored via layout)
    private String handMode = "left";
    private boolean isMirrored() { return "right".equals(handMode); }
    public void setHandMode(String h) { handMode = h; }

    // ── Geometry ──────────────────────────────────────────────────────
    private static final double[] ANGLES_DEG = {144.0, 216.0, 288.0, 360.0, 432.0};
    private static final int[]    MID_ORDER  = {2, 3, 4, 0, 1};
    private static final float    RI_FRAC    = 4f / 7f;
    private static final float    RM_FRAC    = 5.6f / 7f;  // bigger mid ring = smaller outer ring
    private static final float    CAPTURE_CENTER = 0.38f;
    private static final float    CAPTURE_MID    = 0.10f;
    private static final float    CAPTURE_OUTER  = 0.18f;
    private static final float    DC_THRESHOLD   = 40f; // degrees

    // ── Letter layouts ────────────────────────────────────────────────
    static final String[] CENTER_ALPHA = {"A","U","O","I","E"};
    static final String[] MID_ALPHA    = {"F","V","B","Q","N","G","H","T","R","S"};
    static final String[] OUTER_ALPHA  = {",","?","!","J","X","Z","D","Y","K","C","W","M","P","L","."};
    static final String[] CENTER_NUM   = {"0","1","2","3","4"};
    static final String[] MID_NUM      = {"5","6","7","8","9","10",".",",","!","?"};
    static final String[] OUTER_NUM    = {";",":","\""  ,"'","(",")", "_","-","@","#","$","%","&","*","+"};
    static final String[] CENTER_EMOJI = {"😀","😂","😍","😎","🤔"};
    static final String[] MID_EMOJI    = {"😢","😡","🥳","😴","🤗","👍","👎","❤️","🔥","✨"};
    static final String[] OUTER_EMOJI  = {"🎉","💯","🙏","💀","🤣","😊","😏","🥺","😤","🤩","🫡","💪","🤝","👀","🌟"};

    // ── Improved dictionary (frequency-ranked) ─────────────────────────
    static final String[] DICT = {
        "the","of","and","a","to","in","is","it","you","that","he","was","for","on","are",
        "as","with","his","they","at","be","this","from","or","one","had","by","word","but",
        "not","what","all","were","we","when","your","can","said","there","use","an","each",
        "which","she","do","how","their","if","will","up","other","about","out","many","then",
        "them","these","so","some","her","would","make","like","him","into","time","has","look",
        "two","more","go","see","no","way","could","my","than","first","been","its","who","now",
        "did","get","come","made","may","part","over","new","take","place","where","after","back",
        "little","only","around","man","came","just","because","our","right","still","should",
        "tell","every","need","large","often","hand","high","land","give","year","most","live",
        "very","good","same","men","read","long","must","big","next","well","also","set","put",
        "end","why","again","last","never","us","left","turn","move","show","kind","such",
        "form","open","seem","life","old","while","ask","help","near","real","name","work","home",
        "know","play","stop","face","point","line","side","start","full","free","run","own",
        "people","always","talk","walk","hear","change","between","water","food",
        "act","age","air","arm","bad","bag","bed","bit","boy","buy","cut","day","die","dog",
        "eat","eye","fit","fly","fun","got","guy","hit","hot","ice","joy","key","kid","law",
        "lay","leg","let","lie","lot","low","map","mix","mud","nod","odd","off","oil","pay",
        "pet","pie","pop","pot","ran","red","row","sad","sat","saw","say","shy","sit","six",
        "sky","son","sun","tap","tax","ten","tip","toe","top","van","war","wet","win","won",
        "yes","yet","able","area","army","away","baby","ball","band","bank","base","bath",
        "bear","beat","bell","best","bike","bill","bird","bite","blow","blue","body","bone",
        "book","boot","born","both","bowl","burn","busy","calm","card","care","case","cash",
        "cave","cell","chat","chip","city","coal","coat","code","cold","cook","cool","core",
        "corn","cost","dark","data","date","dawn","deal","dear","debt","deep","desk","diet",
        "dirt","dive","door","dose","drop","drug","drum","dust","duty","earn","ease","edge",
        "even","exam","exit","fail","fair","fake","fame","farm","fast","fear","feed","feel",
        "feet","file","fill","film","fine","fire","firm","fish","five","flag","flat","flew",
        "flow","foam","fold","folk","fond","fool","fork","fort","fund","fury","fuse","gain",
        "game","gang","gate","gear","gift","girl","glad","glow","glue","goal","gold","gone",
        "grab","gray","grew","grip","grow","gulf","hack","hail","half","hall","hang","harm",
        "hate","have","head","heal","heap","heat","heel","herb","hero","hide","hill","hint",
        "hire","holy","hook","horn","host","hour","huge","hull","hung","hunt","hurt","inch",
        "iron","item","join","joke","jump","jury","keen","kill","king","kiss","knew","lack",
        "lady","laid","lake","lane","lawn","leaf","lean","leap","lens","lift","limb","link",
        "lion","load","loan","lock","lone","loop","lord","loss","loud","lure","mall","mare",
        "mass","mate","math","maze","meal","meat","mesh","mild","mile","milk","mill","mine",
        "mint","mode","mole","mood","moon","moss","neck","news","node","norm","nose","odds",
        "oven","pair","pale","palm","park","peak","peel","pine","pink","pipe","plot","plug",
        "plus","poem","pole","pond","pool","poor","port","pose","post","pour","prey","prop",
        "pure","rack","rage","raid","rail","rain","rang","rank","rare","rate","reef","reel",
        "rely","rent","rich","ring","riot","ripe","risk","rock","roof","root","rose","ruin",
        "rush","rust","sake","sale","salt","sand","sang","seal","seam","seat","seed","seek",
        "sell","shed","shin","shot","shut","silk","sink","site","skip","slam","slim","slip",
        "snap","sock","soil","sole","sore","span","spin","spot","stem","stew","stir","stun",
        "sung","sunk","swim","tail","tale","tall","tank","tape","task","team","tear","tent",
        "thin","tide","tile","till","tilt","tiny","tire","toll","tone","torn","tour","town",
        "trap","trim","trip","tube","turf","twin","ugly","vain","vary","vast","veil","vein",
        "verb","vest","vice","vine","void","wade","wage","wake","warp","wary","wave","weld",
        "whim","whip","whom","wilt","wipe","wire","wise","woke","wrap","yawn",
        "about","above","after","again","agree","ahead","allow","alone","along","among",
        "anger","apply","arena","argue","arise","aside","basic","beach","begin","being",
        "below","black","blade","blank","blast","bleed","blend","bless","block","blood",
        "blown","board","bonus","boost","bound","brain","brand","brave","bread","break",
        "breed","brief","bring","broke","brown","brush","build","built","bunch","burst",
        "buyer","carry","cause","cease","chain","chair","chaos","chart","chase","check",
        "chest","chief","child","chord","chose","civil","claim","class","clean","clear",
        "click","climb","clock","clone","close","cloud","coach","coast","color","could",
        "count","court","cover","crack","craft","crash","crazy","cream","creek","crime",
        "cross","crowd","crown","crush","curve","cycle","daily","dance","death","delay",
        "dense","depth","devil","dirty","doing","donor","doubt","draft","drain","drama",
        "drawn","dream","dress","drift","drink","drive","drove","dying","eagle","early",
        "earth","eight","elite","empty","enemy","enjoy","enter","entry","equal","error",
        "essay","event","exact","exist","extra","faint","faith","false","fancy","fatal",
        "fault","feast","fence","fever","fiber","field","fifth","fifty","fight","final",
        "first","fixed","flame","flash","fleet","flesh","float","flock","flood","floor",
        "fluid","focus","force","forge","forth","forum","found","frame","frank","fraud",
        "fresh","front","froze","fruit","fully","funny","ghost","giant","given","glass",
        "globe","gloom","glory","going","grace","grade","grand","grant","graph","grasp",
        "grass","grave","great","green","greet","grief","grill","groan","groom","gross",
        "group","guard","guess","guest","guide","guild","guilt","happy","harsh","heart",
        "heavy","hence","human","humor","hurry","image","imply","inbox","index","inner",
        "input","issue","jewel","judge","juice","jumbo","keeps","labor","laser","later",
        "laugh","layer","learn","lease","leave","legal","level","light","limit","local",
        "logic","loose","lover","lucky","lying","magic","major","maker","march","match",
        "mayor","media","mercy","merit","metal","might","minor","model","money","month",
        "moral","motor","mount","mouse","mouth","music","nerve","never","night","noble",
        "noise","north","noted","novel","nurse","ocean","offer","often","order","other",
        "outer","paint","panel","paper","party","peace","pearl","phase","phone","photo",
        "piano","piece","pilot","pitch","place","plain","plane","plant","plate","plaza",
        "point","polar","power","press","price","pride","prime","prior","prize","probe",
        "proof","prose","proud","prove","pulse","punch","pupil","purse","queen","query",
        "quest","queue","quick","quiet","quota","quote","radio","raise","rally","ranch",
        "range","rapid","ratio","reach","ready","realm","rebel","refer","reign","relax",
        "reply","rider","rifle","right","risky","river","robot","rocky","rough","round",
        "route","royal","ruler","rural","saint","salad","scale","scare","scene","score",
        "scout","seize","sense","serve","seven","shade","shake","shall","shame","shape",
        "share","shark","sharp","shelf","shell","shift","shine","shirt","shoot","short",
        "shout","sight","since","sixth","sixty","skill","skull","slave","sleep","slide",
        "slope","small","smart","smell","smile","smoke","solar","solve","sorry","sound",
        "south","space","spare","spark","speak","speed","spell","spend","spine","spite",
        "split","spoke","spoon","sport","spray","squad","staff","stage","stain","stake",
        "stand","stark","start","state","steal","steam","steel","steep","steer","stern",
        "stick","stiff","still","stock","stone","store","storm","story","strap","straw",
        "stray","strip","stuck","study","stuff","style","sugar","suite","sunny","super",
        "surge","swear","sweet","swept","swift","swing","sword","table","taste","teach",
        "thank","their","theme","there","thick","thing","think","third","those","three",
        "throw","thumb","tiger","tight","title","today","token","total","touch","tough",
        "towel","tower","toxic","track","trade","trail","train","trait","trash","trend",
        "trial","tribe","trick","tried","troop","truck","truly","trust","truth","tumor",
        "twist","uncle","under","union","unite","unity","until","upper","upset","urban",
        "usage","usual","valid","value","valve","video","viral","virus","visit","vital",
        "vivid","vocal","voice","voter","wagon","waste","watch","water","weigh","weird",
        "where","which","while","white","whole","whose","wider","woman","women","world",
        "worse","worst","worth","would","write","wrote","youth","zebra",
        "always","animal","answer","before","better","beyond","bottle","bottom","bridge",
        "bright","broken","butter","button","camera","candle","cannot","castle","center",
        "change","choose","chosen","church","circle","closed","coffee","corner","cotton",
        "couple","course","create","credit","damage","dancer","danger","decide","design",
        "detail","dinner","double","dragon","driver","during","easily","either","eleven",
        "empire","enable","enough","entire","escape","estate","except","expect","facing",
        "factor","fallen","family","father","figure","finger","finish","flower","follow",
        "forest","forget","frozen","future","garden","gather","gender","gentle","global",
        "golden","ground","growth","happen","harder","health","hidden","higher","honest",
        "horror","hunger","inside","island","itself","joined","junior","killer","knight",
        "launch","leader","league","letter","listen","living","longer","losing","lowest",
        "manner","market","master","matter","memory","middle","mirror","missing","modern",
        "moment","mother","murder","myself","narrow","nation","nature","needed","number",
        "object","office","online","opened","orange","origin","palace","parent","passed",
        "people","period","person","planet","player","please","plenty","pocket","policy",
        "prefer","pretty","prince","prison","public","pulled","purple","pushed","rather",
        "reason","record","remain","remove","repair","repeat","return","reveal","reward",
        "riding","rising","rubber","ruling","runner","sample","school","screen","secret",
        "select","seller","senior","series","settle","should","signal","silver","simple",
        "single","sister","skills","slowly","smooth","social","source","speech","spring",
        "statue","steady","stolen","street","string","strong","struck","submit","supply",
        "surely","switch","symbol","target","terror","tested","theory","ticket","timber",
        "toward","travel","trying","tunnel","turned","twelve","twenty","typing","unique",
        "unless","useful","valley","varied","vendor","versus","victim","vision","volume",
        "waited","wanted","weapon","weekly","weight","window","winner","winter","within",
        "wonder","wooden","worker","yellow","yields",
        "fucking","fucked","fucker","fuck","shit","shitty","damn","hell","crap","ass","bitch","bastard","bullshit","goddamn","asshole","pissed","screwed","sucks","suck","yeah","nope","yep","nah","okay","ok","hey","hi","bye","cool","nice","wow","omg","lol","haha","dude","bro","guys","man","wait","oops","ugh","hmm","ugh","going","getting","making","taking","coming","being","having","doing","seeing","saying","putting","giving","turning","moving","trying","playing","working","talking","walking","running","feeling","thinking","knowing","loving","hating","wanting","needing","missing","hoping","helping","asking","showing","telling","using","finding","meeting","leaving","starting","stopping","reading","writing","eating","drinking","sleeping","driving","buying","selling","calling","texting","really","totally","literally","actually","basically","probably","definitely","honestly","seriously","absolutely","obviously","clearly","exactly","already","everyone","everything","something","anything","nothing","someone","anyone","somewhere","everywhere","nowhere","somehow","anyway","maybe","perhaps","together","another","however","whatever","whenever","wherever","whoever","morning","evening","tonight","today","tomorrow","yesterday","weekend","monday","tuesday","wednesday","thursday","friday","saturday","sunday","birthday","sorry","thanks","please","welcome","congrats","happy","sad","angry","scared","tired","sick","hungry","thirsty","bored","excited","nervous","worried","phone","message","email","text","call","meeting","appointment","office","money","price","food","drink","coffee","dinner","lunch","breakfast","party","movie","music","game","book","car","house","home","store","restaurant"
    };

    // ── Expected direction changes by word length (from analysis) ─────
    private static float expectedDC(int len) {
        switch(len) {
            case 2: return 0.0f;
            case 3: return 0.8f;
            case 4: return 1.6f;
            case 5: return 2.5f;
            case 6: return 3.6f;
            case 7: return 3.9f;
            case 8: return 4.3f;
            case 9: return 6.0f;
            case 10: return 7.0f;
            default: return len >= 9 ? 6.0f + (len-9)*0.7f : 0f;
        }
    }

    // ── Cell / FnArea ─────────────────────────────────────────────────
    static class Cell {
        float[][] pts; float cx, cy; String ring; int idx; boolean pressed;
        Cell(float[][] pts, String ring, int idx) {
            this.pts=pts; this.ring=ring; this.idx=idx;
            double sx=0, sy=0;
            for (float[] p : pts) { sx+=p[0]; sy+=p[1]; }
            cx=(float)(sx/pts.length); cy=(float)(sy/pts.length);
        }
        boolean contains(float x, float y) {
            boolean in=false; int n=pts.length;
            for (int i=0,j=n-1; i<n; j=i++) {
                float xi=pts[i][0],yi=pts[i][1],xj=pts[j][0],yj=pts[j][1];
                if (((yi>y)!=(yj>y)) && (x<(xj-xi)*(y-yi)/(yj-yi)+xi)) in=!in;
            }
            return in;
        }
        float distTo(float x,float y) { float dx=x-cx,dy=y-cy; return (float)Math.sqrt(dx*dx+dy*dy); }
    }

    private static class FnArea {
        float[][] pts; float cx, cy; String id;
        FnArea(float[][] pts, String id) {
            this.pts=pts; this.id=id;
            double sx=0, sy=0;
            for (float[] p : pts) { sx+=p[0]; sy+=p[1]; }
            cx=(float)(sx/pts.length); cy=(float)(sy/pts.length);
        }
        boolean contains(float x, float y) {
            boolean in=false; int n=pts.length;
            for (int i=0,j=n-1; i<n; j=i++) {
                float xi=pts[i][0],yi=pts[i][1],xj=pts[j][0],yj=pts[j][1];
                if (((yi>y)!=(yj>y)) && (x<(xj-xi)*(y-yi)/(yj-yi)+xi)) in=!in;
            }
            return in;
        }
    }

    // ── State ─────────────────────────────────────────────────────────
    private final List<Cell>   cells   = new ArrayList<>();
    private final List<FnArea> fnAreas = new ArrayList<>();
    private float vCx, vCy, vR, vRm, vRi;
    private float[][] OP, MP, IP;
    private boolean geometryReady = false;

    // ── Letter coordinates for swipe matching ─────────────────────────
    private float[] letterX = new float[26];
    private float[] letterY = new float[26];

    // ── Paints ────────────────────────────────────────────────────────
    private Paint pCellFill, pCellFillCenter, pCellFillMid, pCellFillNum, pCellFillEmoji;
    private Paint pCellStroke, pCellStrokeMid, pCellStrokeNum, pPressed;
    private Paint pTextNormal, pTextCenter, pTextMid, pTextNum, pTextEmoji;
    private Paint pFnFill, pFnFillDel, pFnFillShiftOn, pFnFillEmojiActive;
    private Paint pFnStroke, pFnText, pFnTextDel, pFnTextShiftOn;
    private Paint pSwipeTrail, pSwipeDot, pSwipeCellDot;
    private float MOVE_THRESHOLD;

    // ── Swipe state ───────────────────────────────────────────────────
    private boolean isSwiping = false;
    private final List<PointF> swipeTrail  = new ArrayList<>();
    private final List<Cell>   swipedCells = new ArrayList<>();
    private float touchDownX, touchDownY;
    private boolean touchMoved = false;
    private float swipeTotalLength = 0;
    private float swipeDirChanges  = 0;
    private float prevPtX=-1, prevPtY=-1;
    private float prevVx=0, prevVy=0;

    // ── Constructor ───────────────────────────────────────────────────
    public PentagonKeyboardView(Context context, AttributeSet attrs) { super(context,attrs); init(context); }
    public PentagonKeyboardView(Context context)                      { super(context);       init(context); }

    private void init(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        MOVE_THRESHOLD = 8f * dp;
        pCellFill         = fill(0xFF161208);
        pCellFillCenter   = fill(0xFF1E1608);
        pCellFillMid      = fill(0xFF161008);
        pCellFillNum      = fill(0xFF0E1520);
        pCellFillEmoji    = fill(0xFF1A1020);
        pCellStroke       = stroke(0xFF4A3A18, 1.2f*dp);
        pCellStrokeMid    = stroke(0xFF3A2A10, 1.2f*dp);
        pCellStrokeNum    = stroke(0xFF2A3A5A, 1.2f*dp);
        pPressed          = fill(0xFF3A3018);
        pTextNormal = text(0xFFE8DFC8, 11f*dp);
        pTextCenter = text(0xFFC9A84C, 18f*dp);
        pTextMid    = text(0xFFE8DFC8, 13f*dp);
        pTextNum    = text(0xFF8AC0F0, 13f*dp);
        pTextEmoji  = text(0xFFFFFFFF, 16f*dp);
        pFnFill            = fill(0xFF161208);
        pFnFillDel         = fill(0xFF1E0E0E);
        pFnFillShiftOn     = fill(0xFF0E1E18);
        pFnFillEmojiActive = fill(0xFF1A1028);
        pFnStroke          = stroke(0xFF2A1A08, 1.2f*dp);
        pFnText            = text(0xFF8A7F68, 12f*dp);
        pFnTextDel         = text(0xFFA06060, 18f*dp);
        pFnTextShiftOn     = text(0xFF6ADDB0, 14f*dp);
        pSwipeTrail = new Paint(Paint.ANTI_ALIAS_FLAG);
        pSwipeTrail.setStyle(Paint.Style.STROKE);
        pSwipeTrail.setColor(0x99C9A84C);
        pSwipeTrail.setStrokeWidth(3.5f*dp);
        pSwipeTrail.setStrokeCap(Paint.Cap.ROUND);
        pSwipeTrail.setStrokeJoin(Paint.Join.ROUND);
        pSwipeDot     = fill(0xFFF0D080);
        pSwipeCellDot = fill(0x66C9A84C);
    }

    private Paint fill(int c)           { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.FILL);   p.setColor(c); return p; }
    private Paint stroke(int c, float w){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setColor(c); p.setStrokeWidth(w); return p; }
    private Paint text(int c, float s)  { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(c); p.setTextSize(s); p.setTextAlign(Paint.Align.CENTER); return p; }

    // ── Geometry ──────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w > 0 && h > 0) buildGeometry(w, h);
    }

    private float[] pentPt(double deg, float r) {
        double rad = Math.toRadians(deg);
        return new float[]{ vCx + r*(float)Math.sin(rad), vCy - r*(float)Math.cos(rad) };
    }
    private float[] lerp(float[] a, float[] b, float t) {
        return new float[]{ a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t };
    }
    private float[] centroid(float[]... pts) {
        float sx=0, sy=0;
        for (float[] p : pts) { sx+=p[0]; sy+=p[1]; }
        return new float[]{ sx/pts.length, sy/pts.length };
    }

    private void buildGeometry(int W, int H) {
        cells.clear(); fnAreas.clear();
        vR  = Math.min(W / 1.9021f, H / 1.8090f);
        vRi = vR * RI_FRAC;
        vRm = vR * RM_FRAC;
        vCx = W / 2f;
        vCy = H / 2f;

        OP = new float[5][]; MP = new float[5][]; IP = new float[5][];
        for (int i=0; i<5; i++) {
            OP[i] = pentPt(ANGLES_DEG[i], vR);
            MP[i] = pentPt(ANGLES_DEG[i], vRm);
            IP[i] = pentPt(ANGLES_DEG[i], vRi);
        }

        // Center
        for (int i=0; i<5; i++) {
            int si = (2+i) % 5;
            cells.add(new Cell(new float[][]{IP[si], IP[(si+1)%5], new float[]{vCx, vCy}}, "center", i));
        }
        // Mid
        int mi = 0;
        for (int si=0; si<5; si++) {
            int s = MID_ORDER[si];
            float[] mA=MP[s], mB=MP[(s+1)%5], iA=IP[s], iB=IP[(s+1)%5];
            float[] mM=lerp(mA,mB,.5f), iM=lerp(iA,iB,.5f);
            cells.add(new Cell(new float[][]{mA,mM,iM,iA}, "mid", mi++));
            cells.add(new Cell(new float[][]{mM,mB,iB,iM}, "mid", mi++));
        }
        // Outer
        int oi = 0;
        for (int si=0; si<5; si++) {
            int s = MID_ORDER[si];
            float[] oA=OP[s],oB=OP[(s+1)%5],mA=MP[s],mB=MP[(s+1)%5];
            float[] o1=lerp(oA,oB,1f/3f), o2=lerp(oA,oB,2f/3f);
            float[] m1=lerp(mA,mB,1f/3f), m2=lerp(mA,mB,2f/3f);
            cells.add(new Cell(new float[][]{oA,o1,m1,mA}, "outer", oi++));
            cells.add(new Cell(new float[][]{o1,o2,m2,m1}, "outer", oi++));
            cells.add(new Cell(new float[][]{o2,oB,mB,m2}, "outer", oi++));
        }

        // Fn corners
        float[][] verts = new float[5][];
        for (int i=0; i<5; i++) verts[i] = OP[i].clone();
        java.util.Arrays.sort(verts, (a,b) -> Float.compare(a[1], b[1]));
        float[] vTop = verts[0];
        float[] vUL  = verts[1][0] < verts[2][0] ? verts[1] : verts[2];
        float[] vUR  = verts[1][0] < verts[2][0] ? verts[2] : verts[1];
        float[] vBL  = verts[3][0] < verts[4][0] ? verts[3] : verts[4];
        float[] vBR  = verts[3][0] < verts[4][0] ? verts[4] : verts[3];
        float   midX = vTop[0];

        fnAreas.add(new FnArea(new float[][]{{0,0},{midX,0},vTop,vUL,{0,vUL[1]}}, "del"));
        fnAreas.add(new FnArea(new float[][]{{midX,0},{(float)W,0},{(float)W,vUR[1]},vUR,vTop}, "shift"));
        fnAreas.add(new FnArea(new float[][]{{0,vUL[1]},vUL,vBL,{vBL[0],(float)H},{0,(float)H}}, "emoji"));
        fnAreas.add(new FnArea(new float[][]{ vUR,{(float)W,vUR[1]},{(float)W,(float)H},{vBR[0],(float)H},vBR}, "abc"));

        buildLetterCoords();
        geometryReady = true;
        invalidate();
    }

    private void buildLetterCoords() {
        // Store centroid of each alpha letter cell for swipe matching
        for (Cell c : cells) {
            if (!"alpha".equals(mode) && !"center".equals(c.ring) && !"mid".equals(c.ring) && !"outer".equals(c.ring)) continue;
            String[] src = c.ring.equals("center") ? CENTER_ALPHA : c.ring.equals("mid") ? MID_ALPHA : OUTER_ALPHA;
            if (c.idx < src.length) {
                String lbl = src[c.idx];
                if (lbl.length()==1 && Character.isLetter(lbl.charAt(0))) {
                    int idx = lbl.toUpperCase().charAt(0) - 'A';
                    if (idx >= 0 && idx < 26) { letterX[idx]=c.cx; letterY[idx]=c.cy; }
                }
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!geometryReady) return;
        canvas.drawColor(0xFF0A0806);
        for (Cell c : cells)     drawCell(canvas, c);
        for (FnArea f : fnAreas) drawFn(canvas, f);
        if (isSwiping) drawTrail(canvas);
    }

    private void drawCell(Canvas canvas, Cell cell) {
        // Mirror the shape path if right-hand mode, but draw text at unmirrored position
        Path p = isMirrored() ? toPathMirrored(cell.pts) : toPath(cell.pts);
        canvas.drawPath(p, cell.pressed ? pPressed : getCellFill(cell));
        canvas.drawPath(p, getCellStroke(cell));
        Paint tp = getCellTextPaint(cell);
        String lbl = getCellLabel(cell);
        float tx = isMirrored() ? (getWidth() - cell.cx) : cell.cx;
        canvas.drawText(lbl, tx, cell.cy - (tp.descent()+tp.ascent())/2f, tp);
    }

    private void drawFn(Canvas canvas, FnArea fn) {
        Path p = isMirrored() ? toPathMirrored(fn.pts) : toPath(fn.pts);
        canvas.drawPath(p, getFnFill(fn.id));
        canvas.drawPath(p, pFnStroke);
        Paint tp = getFnTextPaint(fn.id);
        float tx = isMirrored() ? (getWidth() - fn.cx) : fn.cx;
        canvas.drawText(getFnLabel(fn.id), tx, fn.cy - (tp.descent()+tp.ascent())/2f, tp);
    }

    private void drawTrail(Canvas canvas) {
        if (swipeTrail.size() < 2) return;
        Path p = new Path();
        float w = getWidth();
        PointF first = swipeTrail.get(0);
        float fx = isMirrored() ? w - first.x : first.x;
        p.moveTo(fx, first.y);
        for (int i=1; i<swipeTrail.size(); i++) {
            PointF pt = swipeTrail.get(i);
            float px = isMirrored() ? w - pt.x : pt.x;
            p.lineTo(px, pt.y);
        }
        canvas.drawPath(p, pSwipeTrail);
        PointF tip = swipeTrail.get(swipeTrail.size()-1);
        float tx = isMirrored() ? w - tip.x : tip.x;
        canvas.drawCircle(tx, tip.y, 7f, pSwipeDot);
        Set<String> seen = new LinkedHashSet<>();
        for (Cell c : swipedCells) {
            if (seen.add(c.ring+c.idx)) {
                float cx2 = isMirrored() ? w - c.cx : c.cx;
                canvas.drawCircle(cx2, c.cy, 5f, pSwipeCellDot);
            }
        }
    }

    // ── Cell helpers ──────────────────────────────────────────────────
    String getCellLabel(Cell cell) {
        String[] src = getSource(cell);
        String l = src[cell.idx];
        if (mode.equals("alpha") && l.matches("[a-zA-Z]"))
            l = shifted ? l.toUpperCase() : l.toLowerCase();
        return l;
    }
    private String[] getSource(Cell c) {
        if (mode.equals("num"))
            return c.ring.equals("center") ? CENTER_NUM : c.ring.equals("mid") ? MID_NUM : OUTER_NUM;
        if (mode.equals("emoji"))
            return c.ring.equals("center") ? CENTER_EMOJI : c.ring.equals("mid") ? MID_EMOJI : OUTER_EMOJI;
        return c.ring.equals("center") ? CENTER_ALPHA : c.ring.equals("mid") ? MID_ALPHA : OUTER_ALPHA;
    }
    private Paint getCellFill(Cell c) {
        if (mode.equals("num"))          return pCellFillNum;
        if (mode.equals("emoji"))        return pCellFillEmoji;
        if (c.ring.equals("center"))     return pCellFillCenter;
        if (c.ring.equals("mid"))        return pCellFillMid;
        return pCellFill;
    }
    private Paint getCellStroke(Cell c) {
        if (mode.equals("num"))      return pCellStrokeNum;
        if (c.ring.equals("mid"))    return pCellStrokeMid;
        return pCellStroke;
    }
    private Paint getCellTextPaint(Cell c) {
        if (mode.equals("emoji"))        return pTextEmoji;
        if (mode.equals("num"))          return pTextNum;
        if (c.ring.equals("center"))     return pTextCenter;
        if (c.ring.equals("mid"))        return pTextMid;
        return pTextNormal;
    }
    private Paint getFnFill(String id) {
        if ("del".equals(id))   return pFnFillDel;
        if ("shift".equals(id)) return shifted ? pFnFillShiftOn : pFnFill;
        if ("emoji".equals(id)) return mode.equals("emoji") ? pFnFillEmojiActive : pFnFill;
        return pFnFill;
    }
    private Paint getFnTextPaint(String id) {
        if ("del".equals(id))            return pFnTextDel;
        if ("shift".equals(id) && shifted) return pFnTextShiftOn;
        return pFnText;
    }
    private String getFnLabel(String id) {
        switch (id) { case "del": return "⌫"; case "shift": return "⇧"; case "emoji": return "😊"; case "abc": return "abc"; }
        return id;
    }

    // ── Improved word matching ────────────────────────────────────────
    private List<String> matchWord(String rawPath, float swipeLen, float dirChanges) {
        if (rawPath.length() < 1) return new ArrayList<>();
        String raw = rawPath.toUpperCase();
        char first = raw.charAt(0), last = raw.charAt(raw.length()-1);
        int rawLen = raw.length();

        List<long[]> scored = new ArrayList<>();
        for (int wi=0; wi<DICT.length; wi++) {
            String word = DICT[wi];
            int wlen = word.length();
            if (wlen < 2 || wlen > rawLen + 4) continue;

            String wUp = word.toUpperCase();
            boolean fM = wUp.charAt(0)==first, lM = wUp.charAt(wlen-1)==last;
            if (!fM && !lM) continue;

            // Letter order coverage
            int pi=0, matched=0;
            for (int li=0; li<wlen; li++) {
                while (pi<rawLen && raw.charAt(pi)!=wUp.charAt(li)) pi++;
                if (pi<rawLen) { matched++; pi++; }
            }
            if (matched < wlen * 0.6f) continue;

            long score = (long)(wi * 0.2f);   // tuned v2
            if (!fM) score+=100; if (!lM) score+=100;

            // Swipe length score: expected = 0.217 * wlen * vR
            float expLen = 0.217f * wlen * vR;
            float lenDiff = Math.abs(swipeLen - expLen) / Math.max(vR, 1f);
            score += (long)(lenDiff * 100f);  // len_w=100

            // Direction change score
            float expDC = expectedDC(wlen);
            float dcDiff = Math.abs(dirChanges - expDC);
            score += (long)(dcDiff * 10f);   // dc_w=10 (real swipes miss turns)

            // Path length similarity
            score += Math.abs(wlen - rawLen) * 20L;  // path_w=20

            // Bonus for exact first+last
            if (fM && lM) score -= 80;  // fl_bonus=80

            scored.add(new long[]{wi, score});
        }
        scored.sort((a,b) -> Long.compare(a[1],b[1]));
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (long[] s : scored) {
            String w = DICT[(int)s[0]];
            if (seen.add(w)) result.add(w);
            if (result.size()>=3) break;
        }
        return result;
    }

    // ── Touch ─────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float rawX = event.getX(), rawY = event.getY();
        // If mirrored, un-mirror the touch x so it matches our stored geometry
        float x = isMirrored() ? (getWidth() - rawX) : rawX;
        float y = rawY;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX=x; touchDownY=y; touchMoved=false; isSwiping=false;
                swipeTrail.clear(); swipedCells.clear();
                swipeTotalLength=0; swipeDirChanges=0; prevPtX=x; prevPtY=y; prevVx=0; prevVy=0;
                swipeTrail.add(new PointF(x,y));
                Cell c0=cellAt(x,y);
                if(c0!=null&&isLetterCell(c0)){c0.pressed=true;swipedCells.add(c0);invalidate();}
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx=x-touchDownX, dy=y-touchDownY;
                if (!touchMoved&&(Math.abs(dx)>MOVE_THRESHOLD||Math.abs(dy)>MOVE_THRESHOLD)) {
                    touchMoved=true; if(mode.equals("alpha")) isSwiping=true;
                }
                if (isSwiping) {
                    float seg=(float)Math.hypot(x-prevPtX,y-prevPtY);
                    swipeTotalLength+=seg;
                    if (seg>1f) {
                        float vx=(x-prevPtX)/seg, vy=(y-prevPtY)/seg;
                        if (prevVx!=0||prevVy!=0) {
                            float dot=vx*prevVx+vy*prevVy;
                            float angle=(float)(Math.acos(Math.max(-1,Math.min(1,dot)))*180/Math.PI);
                            if (angle > DC_THRESHOLD) swipeDirChanges++;
                        }
                        prevVx=vx; prevVy=vy;
                    }
                    prevPtX=x; prevPtY=y;
                    swipeTrail.add(new PointF(x,y));
                    updateSwipedCells(x,y);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                clearAllPressed();
                if (isSwiping&&swipedCells.size()>=2) {
                    StringBuilder raw=new StringBuilder();
                    for (Cell c : swipedCells) {
                        String[] src=c.ring.equals("center")?CENTER_ALPHA:c.ring.equals("mid")?MID_ALPHA:OUTER_ALPHA;
                        if(c.idx<src.length) raw.append(src[c.idx].toLowerCase());
                    }
                    List<String> suggestions=matchWord(raw.toString(), swipeTotalLength, swipeDirChanges);
                    if(listener!=null) listener.onSwipeWord(suggestions, raw.toString());
                } else {
                    handleTap(x,y);
                }
                isSwiping=false; swipeTrail.clear(); swipedCells.clear();
                swipeTotalLength=0; swipeDirChanges=0; prevPtX=-1; prevPtY=-1; prevVx=0; prevVy=0;
                invalidate();
                return true;
        }
        return false;
    }

    private void updateSwipedCells(float x, float y) {
        if (!mode.equals("alpha")) return;
        Cell best=null; float bestNorm=Float.MAX_VALUE;
        for (Cell c : cells) {
            if (!isLetterCell(c)) continue;
            float d=c.distTo(x,y), radius=getCaptureRadius(c.ring);
            if (d<radius) { float n=d/radius; if(n<bestNorm){bestNorm=n;best=c;} }
        }
        if (best==null) return;
        Cell last=swipedCells.isEmpty()?null:swipedCells.get(swipedCells.size()-1);
        if (last==null) { swipedCells.add(best); best.pressed=true; }
        else if (last!=best) { swipedCells.add(best); best.pressed=true; }
    }

    private float getCaptureRadius(String ring) {
        if ("center".equals(ring)) return vR * CAPTURE_CENTER;
        if ("mid".equals(ring))    return vR * CAPTURE_MID;
        return vR * CAPTURE_OUTER;
    }
    private boolean isLetterCell(Cell c) {
        if (!mode.equals("alpha")) return false;
        String[] src=c.ring.equals("center")?CENTER_ALPHA:c.ring.equals("mid")?MID_ALPHA:OUTER_ALPHA;
        return c.idx<src.length && src[c.idx].matches("[a-zA-Z]");
    }
    private void handleTap(float x, float y) {
        if (listener==null) return;
        for (FnArea fn : fnAreas) {
            if (fn.contains(x,y)) {
                switch(fn.id) {
                    case "del":   listener.onDelete();            return;
                    case "shift": listener.onShiftToggle();       return;
                    case "emoji": listener.onModeToggle("emoji"); return;
                    case "abc":   listener.onModeToggle("alpha"); return;
                }
            }
        }
        Cell c=cellAt(x,y);
        if(c!=null) listener.onKeyPress(getCellLabel(c));
    }
    private Cell cellAt(float x, float y) { for(Cell c:cells) if(c.contains(x,y)) return c; return null; }
    private void clearAllPressed() { for(Cell c:cells) c.pressed=false; }
    private Path toPath(float[][] pts) {
        Path p=new Path(); p.moveTo(pts[0][0],pts[0][1]);
        for(int i=1;i<pts.length;i++) p.lineTo(pts[i][0],pts[i][1]);
        p.close(); return p;
    }
    private Path toPathMirrored(float[][] pts) {
        float w = getWidth();
        Path p=new Path(); p.moveTo(w-pts[0][0],pts[0][1]);
        for(int i=1;i<pts.length;i++) p.lineTo(w-pts[i][0],pts[i][1]);
        p.close(); return p;
    }
}
