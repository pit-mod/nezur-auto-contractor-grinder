package com.nezurstandalone.visual;

import java.awt.Color;

/** Bounded, reusable world-space geometry. Time is continuous and never tied to a render frame count. */
public final class TargetIndicatorGeometry {
    public enum Style { BOX, SWEEP_RING, ORBIT_SQUARE, ENERGY_BLADES }
    public enum ColorMode { DAMAGE, CUSTOM, RAINBOW }
    public static final class Options {
        public Style style = Style.BOX;
        public ColorMode colorMode = ColorMode.DAMAGE;
        public double speed=1, scale=1, opacity=.85, rainbowSpeed=1;
        public int color=0xFFC25DFF;
        public boolean blades=true, particles=true, bladeRainbow, particleRainbow;
        public int bladeColor=0xFFFFFFFF, particleColor=0xFFFF5D1E, sparkColor=0xFFFFFFFF, bladeCount=3, particleCount=18;
        public double bladeSpeed=1, bladeWidth=.18, bladeGlow=1, particleSpeed=1, particleSize=1, particleGlow=1;
    }
    public static final class Mesh {
        public static final int CAPACITY=32768;
        public final float[] positions=new float[CAPACITY*3];
        public final int[] colors=new int[CAPACITY];
        public int count, primitive;
        private void reset(int primitive) { count=0; this.primitive=primitive; }
        private void vertex(double x,double y,double z,int color) {
            if(count==CAPACITY)throw new IllegalStateException("Target visual vertex budget exceeded");
            int at=count*3;positions[at]=(float)x;positions[at+1]=(float)y;positions[at+2]=(float)z;colors[count++]=color;
        }
    }
    private static final double TAU=Math.PI*2;
    private static final double[] RING_COS=circle(96,false),RING_SIN=circle(96,true);
    private static final double[] DISC_COS=circle(12,false),DISC_SIN=circle(12,true);
    private static final double[] BLADE_TAPER=new double[49];
    static { for(int i=0;i<BLADE_TAPER.length;i++)BLADE_TAPER[i]=bladeTaper(i/48.0); }
    private final Mesh mesh=new Mesh();
    private final double[] bx=new double[49],by=new double[49],bz=new double[49],ux=new double[49],uy=new double[49],uz=new double[49],taper=new double[49];
    private double rx,ry,rz,vx,vy,vz;
    public Mesh build(Options o,double seconds,double width,double height,float cameraYaw,float cameraPitch,boolean hurt,int seed) {
        Style style=o.style==null?Style.BOX:o.style;
        mesh.reset(style==Style.BOX?1:4);
        if(!Double.isFinite(seconds))seconds=0;
        width=limit(width,.1,5,.6);height=limit(height,.1,5,1.8);
        double speed=limit(o.speed,.2,3,1),scale=limit(o.scale,.6,1.8,1),opacity=limit(o.opacity,.1,1,.85);
        double yaw=Math.toRadians(Float.isFinite(cameraYaw)?cameraYaw:0),pitch=Math.toRadians(Float.isFinite(cameraPitch)?cameraPitch:0);
        rx=Math.cos(yaw);ry=0;rz=Math.sin(yaw);
        vx=-Math.sin(yaw)*Math.sin(pitch);vy=Math.cos(pitch);vz=Math.cos(yaw)*Math.sin(pitch);
        double time=seconds*speed, radius=(width*.5+.32)*scale;
        int color=mainColor(o,seconds,hurt);
        switch(style) {
            case BOX: box(width,height,color,opacity);break;
            case SWEEP_RING: ring(time,radius,height,color,opacity);break;
            case ORBIT_SQUARE: square(time,radius,height,color,opacity);break;
            case ENERGY_BLADES:
                if(o.blades)blades(o,time,seconds,radius,height,opacity);
                if(o.particles)particles(o,time,seconds,radius,height,opacity,seed);
                break;
        }
        return mesh;
    }
    public static double ringHeight(double time,double height) { return .06+Math.max(0,height-.12)*(.5-.5*Math.cos(time*2.2)); }
    public static double bladeTaper(double u) { return Math.pow(Math.max(0,Math.sin(Math.PI*u)),.85); }
    public static int rainbow(double seconds,double speed,double offset) { return Color.HSBtoRGB((float)((seconds*.12*limit(speed,.2,3,1)+offset)%1),.72f,1); }
    private static int mainColor(Options o,double time,boolean hurt) {
        if(o.colorMode==ColorMode.RAINBOW)return rainbow(time,o.rainbowSpeed,0);
        return o.colorMode==ColorMode.CUSTOM?o.color:hurt?0xFFFF4D66:0xFF4DFF66;
    }
    private void box(double width,double height,int rgb,double alpha) {
        double r=width*.5;int c=rgba(rgb,alpha);
        for(int y=0;y<2;y++)for(int i=0;i<4;i++) {
            double a=i*Math.PI*.5+Math.PI*.25,b=(i+1)*Math.PI*.5+Math.PI*.25;
            mesh.vertex(Math.cos(a)*r*Math.sqrt(2),height*y,Math.sin(a)*r*Math.sqrt(2),c);
            mesh.vertex(Math.cos(b)*r*Math.sqrt(2),height*y,Math.sin(b)*r*Math.sqrt(2),c);
        }
        for(int i=0;i<4;i++){double a=i*Math.PI*.5+Math.PI*.25,x=Math.cos(a)*r*Math.sqrt(2),z=Math.sin(a)*r*Math.sqrt(2);
            mesh.vertex(x,0,z,c);mesh.vertex(x,height,z,c);}
    }
    private void ring(double time,double radius,double height,int rgb,double opacity) {
        double y=ringHeight(time,height),trail=Math.min(.38,height*.22)*Math.sin(time*2.2);
        for(int layer=0;layer<8;layer++) {
            double a=layer/8.0,b=(layer+1)/8.0;
            int c1=rgba(rgb,opacity*.20*Math.pow(1-a,2)),c2=rgba(rgb,opacity*.20*Math.pow(1-b,2));
            for(int i=0;i<96;i++) {
                double ya=Math.max(.01,Math.min(height,y-trail*a)),yb=Math.max(.01,Math.min(height,y-trail*b));
                quad(RING_COS[i]*radius,ya,RING_SIN[i]*radius,RING_COS[i+1]*radius,ya,RING_SIN[i+1]*radius,
                        RING_COS[i+1]*radius,yb,RING_SIN[i+1]*radius,RING_COS[i]*radius,yb,RING_SIN[i]*radius,c1,c1,c2,c2);
            }
        }
        // Cross-section bands make the rim softly luminous from both side and top views.
        for(int band=-5;band<5;band++) {
            double a=band/5.0,b=(band+1)/5.0,wa=.075*a,wb=.075*b;
            int ca=rgba(rgb,opacity*.42*Math.exp(-a*a*4)),cb=rgba(rgb,opacity*.42*Math.exp(-b*b*4));
            for(int i=0;i<96;i++){
                quad(RING_COS[i]*(radius+wa),y,RING_SIN[i]*(radius+wa),RING_COS[i+1]*(radius+wa),y,RING_SIN[i+1]*(radius+wa),
                        RING_COS[i+1]*(radius+wb),y,RING_SIN[i+1]*(radius+wb),RING_COS[i]*(radius+wb),y,RING_SIN[i]*(radius+wb),ca,ca,cb,cb);
                quad(RING_COS[i]*radius,y+wa,RING_SIN[i]*radius,RING_COS[i+1]*radius,y+wa,RING_SIN[i+1]*radius,
                        RING_COS[i+1]*radius,y+wb,RING_SIN[i+1]*radius,RING_COS[i]*radius,y+wb,RING_SIN[i]*radius,ca,ca,cb,cb);
            }
        }
    }
    private void square(double time,double radius,double height,int rgb,double opacity) {
        double size=radius*.80*(1+.018*Math.sin(time*.95)),angle=time*.72;
        double c=Math.cos(angle),s=Math.sin(angle),y=height*.56;
        double round=size*.18,gap=size*.22,cap=size*.10,glowWidth=size*.082,coreWidth=size*.017;
        for(int i=0;i<4;i++) {
            double sx=(i==0||i==3)?-1:1,sy=i<2?-1:1;
            // Reuse the ribbon scratch buffers for one continuous, rounded corner.
            squarePoint(0,sx*gap,sy*size,0,sy,0);
            squarePoint(1,sx*(gap+cap),sy*size,0,sy,1);
            for(int step=0;step<=8;step++) {
                double a=step*Math.PI/16,nx=sx*Math.sin(a),ny=sy*Math.cos(a);
                squarePoint(step+2,sx*(size-round)+round*nx,sy*(size-round)+round*ny,nx,ny,1);
            }
            squarePoint(11,sx*size,sy*(gap+cap),sx,0,1);
            squarePoint(12,sx*size,sy*gap,sx,0,0);
            for(int band=-6;band<6;band++) {
                double a=band/6.0,b=(band+1)/6.0;
                squareRibbon(glowWidth*a,glowWidth*b,rgb,
                        Math.abs(a)==1?0:opacity*.28*Math.exp(-a*a*4.5),
                        Math.abs(b)==1?0:opacity*.28*Math.exp(-b*b*4.5),c,s,y);
            }
            int core=mixWhite(rgb,.40);
            squareRibbon(-coreWidth,0,core,opacity*.50,opacity*.95,c,s,y);
            squareRibbon(0,coreWidth,core,opacity*.95,opacity*.50,c,s,y);
        }
    }
    private void squarePoint(int i,double x,double y,double nx,double ny,double fade) {
        bx[i]=x;by[i]=y;ux[i]=nx;uy[i]=ny;taper[i]=fade;
    }
    private void squareRibbon(double left,double right,int rgb,double alphaLeft,double alphaRight,double c,double s,double y) {
        for(int i=0;i<12;i++) {
            double ax=bx[i]+ux[i]*left,ay=by[i]+uy[i]*left;
            double bx1=bx[i+1]+ux[i+1]*left,by1=by[i+1]+uy[i+1]*left;
            double cx=bx[i+1]+ux[i+1]*right,cy=by[i+1]+uy[i+1]*right;
            double dx=bx[i]+ux[i]*right,dy=by[i]+uy[i]*right;
            billboardQuad(0,y,0,c*ax-s*ay,s*ax+c*ay,c*bx1-s*by1,s*bx1+c*by1,
                    c*cx-s*cy,s*cx+c*cy,c*dx-s*dy,s*dx+c*dy,
                    rgba(rgb,alphaLeft*taper[i]),rgba(rgb,alphaLeft*taper[i+1]),
                    rgba(rgb,alphaRight*taper[i+1]),rgba(rgb,alphaRight*taper[i]));
        }
    }
    private void blades(Options o,double time,double wallTime,double radius,double height,double opacity) {
        int count=Math.max(1,Math.min(5,o.bladeCount));double width=limit(o.bladeWidth,.06,.3,.18),glow=limit(o.bladeGlow,.3,2.5,1);
        for(int blade=0;blade<count;blade++) {
            double phase=time*limit(o.bladeSpeed,.2,3,1)*(3.1+blade*.32)*(blade%2==0?1:-1)+blade*TAU/count;
            double axis=time*.24+blade*2.1,tilt=(blade%2==0?1:-1)*(.75+blade*.22)+Math.sin(time*.45+blade)*.22;
            double cy=Math.cos(axis),sy=Math.sin(axis),ct=Math.cos(tilt),st=Math.sin(tilt),h=height*(.48+.22*Math.sin(time*.66+blade*2.1));
            int rgb=o.bladeRainbow?rainbow(wallTime,o.rainbowSpeed,blade*.17):o.bladeColor;
            for(int i=0;i<=48;i++) {
                double u=i/48.0,a=phase+(u-.5)*1.85,dx=Math.cos(a),dz=Math.sin(a);
                ux[i]=cy*dx-sy*ct*dz;uy[i]=st*dz;uz[i]=sy*dx+cy*ct*dz;
                bx[i]=ux[i]*radius;by[i]=h+uy[i]*radius;bz[i]=uz[i]*radius;taper[i]=BLADE_TAPER[i];
            }
            for(int band=-4;band<4;band++) {
                double a=band/4.0,b=(band+1)/4.0;
                ribbon(width*2.6*a,width*2.6*b,rgb,opacity*glow*.34*Math.exp(-a*a*4),opacity*glow*.34*Math.exp(-b*b*4));
            }
            int core=mixWhite(rgb,.78);
            ribbon(-width*.30,0,core,opacity*.9,opacity);
            ribbon(0,width*.30,core,opacity,opacity*.9);
        }
    }
    private void ribbon(double left,double right,int rgb,double alphaLeft,double alphaRight) {
        for(int i=0;i<48;i++) {
            double a=taper[i],b=taper[i+1],fadeA=a*a,fadeB=b*b;
            quad(bx[i]+ux[i]*left*a,by[i]+uy[i]*left*a,bz[i]+uz[i]*left*a,
                    bx[i+1]+ux[i+1]*left*b,by[i+1]+uy[i+1]*left*b,bz[i+1]+uz[i+1]*left*b,
                    bx[i+1]+ux[i+1]*right*b,by[i+1]+uy[i+1]*right*b,bz[i+1]+uz[i+1]*right*b,
                    bx[i]+ux[i]*right*a,by[i]+uy[i]*right*a,bz[i]+uz[i]*right*a,
                    rgba(rgb,alphaLeft*fadeA),rgba(rgb,alphaLeft*fadeB),rgba(rgb,alphaRight*fadeB),rgba(rgb,alphaRight*fadeA));
        }
    }
    private void particles(Options o,double time,double wallTime,double radius,double height,double opacity,int seed) {
        int count=Math.max(0,Math.min(36,o.particleCount));double speed=limit(o.particleSpeed,.2,3,1),size=limit(o.particleSize,.4,2,1),glow=limit(o.particleGlow,.3,2.5,1);
        for(int i=0;i<count;i++) {
            double hash=fract(Math.sin((i+seed*.013)*17.73+1.37)*43758.5453),life=1.25+hash*.9;
            double progress=fract(time*speed/life+i*.618),fade=Math.pow(Math.sin(progress*Math.PI),2);
            double a=time*speed*(.7+hash)+i*2.399963,r=radius*(.80+hash*.40),y=.08+height*(progress*.95);
            double x=Math.cos(a)*r,z=Math.sin(a)*r;boolean star=i%3==0;
            int rgb=o.particleRainbow?rainbow(wallTime,o.rainbowSpeed,i*.071):(star?o.sparkColor:o.particleColor);
            double extent=(star?.065:.045)*size*(.75+hash*.5);
            glowDisc(x,y,z,extent*2.6,rgb,opacity*fade*glow*.34);
            if(star) {
                // Orthogonal, tapered rays with smaller diagonals give distinct white star sparks.
                starRay(x,y,z,extent,extent*.17,0,rgb,opacity*fade);
                starRay(x,y,z,extent,extent*.17,Math.PI*.5,rgb,opacity*fade);
                starRay(x,y,z,extent*.52,extent*.12,Math.PI*.25,rgb,opacity*fade*.65);
                starRay(x,y,z,extent*.52,extent*.12,Math.PI*.75,rgb,opacity*fade*.65);
            } else glowDisc(x,y,z,extent*.6,mixWhite(rgb,.40),opacity*fade*.85);
        }
    }
    private void glowDisc(double x,double y,double z,double radius,int rgb,double opacity) {
        for(int ring=0;ring<3;ring++) {
            double a=ring/3.0,b=(ring+1)/3.0;
            int ca=rgba(rgb,opacity*Math.exp(-a*a*4)),cb=rgba(rgb,ring==2?0:opacity*Math.exp(-b*b*4));
            for(int i=0;i<12;i++){
                billboardQuad(x,y,z,DISC_COS[i]*radius*a,DISC_SIN[i]*radius*a,DISC_COS[i+1]*radius*a,DISC_SIN[i+1]*radius*a,
                        DISC_COS[i+1]*radius*b,DISC_SIN[i+1]*radius*b,DISC_COS[i]*radius*b,DISC_SIN[i]*radius*b,ca,ca,cb,cb);}
        }
    }
    private void starRay(double x,double y,double z,double length,double width,double angle,int rgb,double opacity) {
        double c=Math.cos(angle),s=Math.sin(angle);int bright=rgba(rgb,opacity),zero=rgba(rgb,0);
        billboardQuad(x,y,z,-length*c,-length*s,-width*s,width*c,length*c,length*s,width*s,-width*c,zero,bright,zero,bright);
    }
    private void billboardQuad(double x,double y,double z,double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy,int ca,int cb,int cc,int cd) {
        quad(x+rx*ax+vx*ay,y+ry*ax+vy*ay,z+rz*ax+vz*ay,x+rx*bx+vx*by,y+ry*bx+vy*by,z+rz*bx+vz*by,
                x+rx*cx+vx*cy,y+ry*cx+vy*cy,z+rz*cx+vz*cy,x+rx*dx+vx*dy,y+ry*dx+vy*dy,z+rz*dx+vz*dy,ca,cb,cc,cd);
    }
    private void quad(double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz,int ca,int cb,int cc,int cd) {
        mesh.vertex(ax,ay,az,ca);mesh.vertex(bx,by,bz,cb);mesh.vertex(cx,cy,cz,cc);
        mesh.vertex(ax,ay,az,ca);mesh.vertex(cx,cy,cz,cc);mesh.vertex(dx,dy,dz,cd);
    }
    private static int mixWhite(int rgb,double amount) {
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        return (rgb&0xFF000000)|((int)(r+(255-r)*amount)<<16)|((int)(g+(255-g)*amount)<<8)|(int)(b+(255-b)*amount);
    }
    private static int rgba(int rgb,double alpha) { return ((int)Math.round(Math.max(0,Math.min(1,alpha))*((rgb>>>24)&255))<<24)|(rgb&0xFFFFFF); }
    private static double fract(double value) { return value-Math.floor(value); }
    private static double[] circle(int segments,boolean sine) {
        double[] values=new double[segments+1];
        for(int i=0;i<=segments;i++){double angle=i*TAU/segments;values[i]=sine?Math.sin(angle):Math.cos(angle);}
        return values;
    }
    private static double limit(double value,double min,double max,double fallback) { return Double.isFinite(value)?Math.max(min,Math.min(max,value)):fallback; }
}
