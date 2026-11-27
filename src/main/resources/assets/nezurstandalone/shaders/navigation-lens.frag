#version 120
uniform sampler2D content;
uniform vec2 captureOrigin, captureSize, lensCenter, size;
uniform float radius, magnification, held, appear, pixelsPerUnit;
uniform float backdrop;
uniform vec3 tint;
varying vec2 local;
vec3 sampleScene(vec2 point) {
    return texture2D(content,clamp((point-captureOrigin)/captureSize,vec2(0.001),vec2(0.999))).rgb;
}
void main() {
    vec2 p=(local-0.5)*size, halfSize=size*0.5;
    vec2 q=abs(p)-(halfSize-vec2(radius));
    float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
    float aa=max(fwidth(d),0.25);
    float coverage=1.0-smoothstep(-aa,aa,d);
    if(coverage<=0.0)discard;
    vec2 normal=p-clamp(p,-halfSize+vec2(radius),halfSize-vec2(radius));
    normal=length(normal)>0.0001?normalize(normal):vec2(0.0);
    float edge=exp(min(d,0.0)/max(1.0,radius*.18));
    vec2 samplePoint=lensCenter+(gl_FragCoord.xy-lensCenter)/magnification;
    vec2 opticalNormal=vec2(normal.x,-normal.y);
    float shoulder=exp(-pow((d+radius*.10)/max(1.0,radius*.12),2.0));
    // Keep refraction on the thin shoulder; the centre and nearby labels stay legible.
    float thickness=radius*(.025+held*.085);
    vec2 bend=opticalNormal*shoulder*thickness*pixelsPerUnit;
    samplePoint+=backdrop>.5?opticalNormal*edge*.25*pixelsPerUnit:bend;
    vec3 body=sampleScene(samplePoint);
    if(backdrop>.5) {
        // Frost the whole track, retaining the real scene and live GUI tint.
        vec2 blur=vec2(1.2*pixelsPerUnit);
        body=body*.28;
        body+=(sampleScene(samplePoint+vec2(blur.x,0))+sampleScene(samplePoint-vec2(blur.x,0))
             +sampleScene(samplePoint+vec2(0,blur.y))+sampleScene(samplePoint-vec2(0,blur.y)))*.12;
        body+=(sampleScene(samplePoint+blur)+sampleScene(samplePoint-blur)
             +sampleScene(samplePoint+vec2(blur.x,-blur.y))+sampleScene(samplePoint+vec2(-blur.x,blur.y)))*.06;
        body=mix(body,tint,.20);
        body=mix(body,vec3(.48,.53,.62),.14)+vec3(.010)*(1.0-local.y);
    } else {
        vec2 split=opticalNormal*shoulder*radius*(.0025+held*.007)*pixelsPerUnit;
        body.r=sampleScene(samplePoint+split).r;
        body.b=sampleScene(samplePoint-split).b;
        vec2 reflected=samplePoint+opticalNormal*radius*.025*pixelsPerUnit;
        vec3 reflection=sampleScene(reflected);
        body=mix(body,reflection,shoulder*(.015+held*.035));
    }
    float rim=exp(-abs(d+0.55)*1.6);
    float catchLight=pow(max(0.0,dot(normal,normalize(vec2(-0.5,-1.0)))),2.0);
    body+=vec3(0.014+held*0.008);
    body+=vec3(rim*(0.07+held*0.045+backdrop*.025)*(0.35+catchLight));
    float inner=exp(-abs(d+1.4)*1.8);
    body+=vec3(.018,.021,.028)*inner*(1.0-local.y);
    float glint=pow(max(0.0,dot(normal,normalize(vec2(-.65,-1.0)))),6.0);
    body+=vec3(glint*edge*(.012+held*.025));
    body-=vec3(exp(-abs(d+.12)*3.0)*.035);
    body+=vec3(edge*(1.0-local.y)*held*0.010);
    body-=vec3(edge*local.y*0.012);
    gl_FragColor=vec4(body,coverage*appear);
}
