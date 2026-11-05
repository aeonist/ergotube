package app.freshtuber.ui.liquid

/**
 * AGSL port of the approved WebGL reference. Coordinates are pixels with a top-left origin;
 * [uScale] maps a layer's own pixels back to screen pixels. Grain is a pre-baked tile
 * ([GrainTile]), not a shader.
 */
internal object LiquidShaders {
    private const val COMMON = """
float sdBox(float2 p,float2 b,float r){float2 q=abs(p)-b+r;return length(max(q,0.))+min(max(q.x,q.y),0.)-r;}
float sdR(float2 p,float4 R,float r){return sdBox(p-(R.xy+R.zw*.5),R.zw*.5,r);}
"""

    /**
     * Explicit bilinear sampling of the small light pictures: four texel centres mixed in the
     * shader, so the result is smooth whatever filtering the GPU applies to child shaders.
     * Nearest sampling of a 12×7 picture, bent by the warp, looks like shifted tiles.
     */
    private val BILINEAR = listOf("a0", "a1", "a2", "b0", "b1", "b2", "c0", "c1", "c2", "d0", "d1", "d2").joinToString("\n") { c ->
        "float3 bl_$c(float2 q){float2 g=q-.5;float2 b=floor(g)+.5;float2 f=g-floor(g);" +
            "return mix(mix($c.eval(b).rgb,$c.eval(b+float2(1.,0.)).rgb,f.x),mix($c.eval(b+float2(0.,1.)).rgb,$c.eval(b+float2(1.,1.)).rgb,f.x),f.y);}"
    }

    /** Uniforms and functions shared by the field and the drops; [octaves] of the warp noise. */
    private fun core(octaves: Int) = """
uniform float2 uRes;
uniform float uT;
uniform float uFlow;
uniform float uDark;
uniform float uMode;
uniform float uBlur;
uniform float uSurge;
uniform float uWob;
uniform float uPx;
uniform float uRefr;
uniform float uScale;
uniform float4 uW;
uniform float4 uRect;
uniform float4 uVid;
uniform float4 uP0;
uniform float4 uP1;
uniform float uR0;
uniform float uR1;
uniform float2 uS0;
uniform float2 uS1;
uniform float2 uS2;
uniform shader a0; uniform shader a1; uniform shader a2;
uniform shader b0; uniform shader b1; uniform shader b2;
uniform shader c0; uniform shader c1; uniform shader c2;
uniform shader d0; uniform shader d1; uniform shader d2;
uniform shader uNoise;
${COMMON}
${BILINEAR}
// Value noise from a random texture, read at texel centres. A float hash breaks on mobile GPUs:
// neighbouring cells hash the same corner slightly differently and the warp tears into tiles.
float nz(float2 i){return uNoise.eval(i+.5).r;}
float vn(float2 p){float2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
 return mix(mix(nz(i),nz(i+float2(1.,0.)),f.x),mix(nz(i+float2(0.,1.)),nz(i+float2(1.,1.)),f.x),f.y);}
float fbm(float2 p){float s=0.,a=.5;for(int i=0;i<${octaves};i++){s+=a*vn(p);p=p*2.03+17.1;a*=.5;}return s;}
float3 lin(float3 c){return pow(max(c,0.),float3(2.2));}
float3 srgb(float3 c){return pow(max(c,0.),float3(1./2.2));}
// Continuous caustics: no mod() wrap of the domain, and constant numerators (the reference's
// mean value) instead of the position, which jumped at every wrap.
float caus(float2 uv,float t){float2 p=uv*6.2831-250.;float2 i=p;float c=1.;
 for(int n=0;n<4;n++){float tt=t*(1.-(3.5/float(n+1)));i=p+float2(cos(tt-i.x)+sin(tt+i.y),sin(tt-i.y)+cos(tt+i.x));
  c+=1./length(float2(-246.86/(sin(i.x+tt)/.005),-246.86/(cos(i.y+tt)/.005)));}
 c/=4.;c=1.17-pow(c,1.4);return pow(abs(c),8.);}
float3 lv0(float2 uv){float2 q=uv*uS0;float3 s=float3(0.);
 if(uW.x>.0005)s+=uW.x*bl_a0(q); if(uW.y>.0005)s+=uW.y*bl_b0(q);
 if(uW.z>.0005)s+=uW.z*bl_c0(q); if(uW.w>.0005)s+=uW.w*bl_d0(q); return s;}
float3 lv1(float2 uv){float2 q=uv*uS1;float3 s=float3(0.);
 if(uW.x>.0005)s+=uW.x*bl_a1(q); if(uW.y>.0005)s+=uW.y*bl_b1(q);
 if(uW.z>.0005)s+=uW.z*bl_c1(q); if(uW.w>.0005)s+=uW.w*bl_d1(q); return s;}
float3 lv2(float2 uv){float2 q=uv*uS2;float3 s=float3(0.);
 if(uW.x>.0005)s+=uW.x*bl_a2(q); if(uW.y>.0005)s+=uW.y*bl_b2(q);
 if(uW.z>.0005)s+=uW.z*bl_c2(q); if(uW.w>.0005)s+=uW.w*bl_d2(q); return s;}
// LOD 4.5 is the first level; each further level is two LOD steps blurrier.
float3 frame(float2 uv,float lod){float t=clamp((lod-4.5)*.5,0.,2.);float3 c;
 if(t<1.){c=mix(lv0(uv),lv1(uv),t);}else{c=mix(lv1(uv),lv2(uv),t-1.);}
 return lin(c);}
float3 throwLight(float2 p,out float k){
 if(uMode<.5){
  float2 c=uRect.xy+uRect.zw*.5;float2 q=(p-c)/uRect.zw+.5;
  float d=max(sdR(p,uRect,20.*uPx),0.)/uRes.y; d*=mix(1.,mix(1.9,3.6,uDark),smoothstep(0.,uRes.y*.12,uRect.y-p.y));
  float lod=4.5+3.*clamp(d*3.,0.,1.)+uBlur; float2 uv=clamp(.5+(q-.5)/(1.+d*1.6),.03,.97);
  k=exp(-d*3.4); return frame(uv,lod);
 }
 float d=max(p.y-(uVid.y+uVid.w),0.)/uRes.y;
 float2 pe=float2(p.x,mix(p.y,uVid.y+uVid.w*.94,clamp(d*1.2,0.,.97)));
 pe.x=mix(pe.x,uVid.x+uVid.z*.5,clamp(d*.6,0.,.6));
 k=exp(-d*2.4);
 float2 uv=clamp((pe-uVid.xy)/uVid.zw,.03,.97);
 return frame(uv,5.+2.5*clamp(d*2.,0.,1.));
}
float3 scene(float2 p){
 float2 w=float2(fbm(p/(uRes.y*.32)+float2(uFlow*.035,0.)),fbm(p/(uRes.y*.32)+float2(7.3,-uFlow*.03)))-.5;
 float2 pw=p+w*uRes.y*(.22+uSurge*.35+uBlur*.05);
 float k;float3 L=throwLight(pw,k);float kd=k*k;
 float lum=dot(L,float3(.2126,.7152,.0722));float3 hue=clamp(L/max(lum,1e-3),0.,3.);
 float3 warm=mix(clamp(hue*.75+.25,0.,1.6),float3(1.,.74,.44),.35);
 float cs=caus(p/uRes.y*1.25+float2(0.,uFlow*.01),uFlow*.22);
 float3 base=float3(.72,.69,.65);
 float3 lt=base*mix(float3(1.),mix(float3(1.),warm*1.1,.62),k)+L*k*.14+warm*cs*.085*(.4+k);
 float kb=exp(-(uRes.y-p.y)/uRes.y*2.6);
 float3 dk=(float3(.004,.003,.0025)+L*(kd*.42+kb*.3)+warm*L*cs*.35*(kd+kb))*.8;
 return mix(lt,dk,smoothstep(0.,1.,uDark));
}
"""

    /**
     * The light field without drop interiors, rendered small (about a quarter of the screen
     * width) and scaled up: it is blurry by nature. Drop shadows outside the drops stay here.
     */
    val FIELD = core(4) + """
float3 dropOut(float2 p,float4 R,float r,float3 col){
 if(R.z<1.||R.w<1.)return col;
 float s=sdR(p,R,r); if(s<=0.)return col;
 float g=exp(-s/(10.*uPx))*smoothstep(0.,1.,(p.y-R.y)/R.w);
 return col+col*.25*g*(1.-uDark*.3)-float3(.05)*exp(-s/(16.*uPx))*(1.-uDark)*.35;
}
half4 main(float2 fc){
 float2 p=fc*uScale;
 if(uMode>.5&&p.x>=uVid.x&&p.x<=uVid.x+uVid.z&&p.y>=uVid.y&&p.y<=uVid.y+uVid.w)return half4(0.,0.,0.,1.);
 float3 col=scene(p);
 col=dropOut(p,uP0,uR0,col);
 col=dropOut(p,uP1,uR1,col);
 return half4(half3(srgb(col)),1.);
}
"""

    /**
     * One drop's interior with refraction. It is drawn only as the drop's rounded rect, so the
     * shader runs on the drop's pixels alone.
     */
    val DROP = core(3) + """
uniform float4 uD;
uniform float uRd;
half4 main(float2 fc){
 float2 p=fc*uScale;
 float4 R=uD;float r=uRd;
 float s=sdR(p,R,r);
 if(s>0.)return half4(0.);
 float2 gr=float2(sdR(p+float2(1.,0.),R,r)-sdR(p-float2(1.,0.),R,r),sdR(p+float2(0.,1.),R,r)-sdR(p-float2(0.,1.),R,r));
 float2 n=normalize(gr+1e-5);
 float edge=smoothstep(-18.*uPx,0.,s);float2 c=R.xy+R.zw*.5;float3 rc;
 if(uRefr>.5){
  float2 off=-n*edge*edge*26.*uPx-(p-c)*.07+float2(sin(p.x*.045/uPx+uT*2.6),cos(p.y*.05/uPx+uT*2.1))*uWob*3.*uPx;
  rc=float3(scene(p+off*1.06).r,scene(p+off).g,scene(p+off*.94).b);
 } else { rc=scene(p); }
 float lum=dot(rc,float3(.2126,.7152,.0722));float3 tint=mix(float3(1.03,.95,.84),float3(1.08,.86,.62),uDark);
 rc=mix(rc,float3(lum),.15)*tint+mix(float3(.06,.05,.04),float3(.012,.008,.005),uDark);rc*=mix(1.,.8,uDark);
 float rim=smoothstep(-2.2*uPx,0.,s)*clamp(-n.y*.8+.35,0.,1.);float lo=smoothstep(-7.*uPx,0.,s)*clamp(n.y,0.,1.);
 return half4(half3(srgb(rc+float3(1.,.96,.9)*rim*mix(.5,.3,uDark)-rc*lo*.25)),1.);
}
"""

}
