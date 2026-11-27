#version 120
varying vec2 local;
void main() { local = gl_MultiTexCoord0.xy; gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }
