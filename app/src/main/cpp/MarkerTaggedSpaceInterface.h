//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_MARKERTAGGEDSPACEINTERFACE_H
#define ARUCOSLAM_MARKERTAGGEDSPACEINTERFACE_H


#include <jni.h>
#include <string>

/**
 * Z                           boolean
 * B                           byte
 * C                           char
 * S                           short
 * I                           int
 * J                           long
 * F                           float
 * D                           double
 * Lfully-qualified-class;     object
 * [type                       array of type
 * (arg-types)ret-type         method type
 */

enum JPrimitiveTypes {
    Boolean, Byte, Char, Short, Integer, Long, Float, Double, Object, Array, //Method
};

#define JSignature(...) (__VA_ARGS__)

#define CPPSignature(...) <__VA_ARGS__>

#define javaMethodCall(javaReturnType, name, javaArguments, ...) \
        this-> javaMethodAsCallable __VA_ARGS__ (compileSignature(JType(javaReturnType), javaArguments), name, &JNIEnv::Call##javaReturnType##MethodA )

#define javaMethod(name, CPPTypes, javaReturnType, javaArgumentTypes, ...) \
        name(__VA_ARGS__){\
            return javaMethodCall(javaReturnType, #name, javaArgumentTypes, < ##CPPTypes## >)(__VA_ARGS__);\
        }


char compilePrimitiveType(JPrimitiveTypes jType) {
    switch (jType) {
        case JPrimitiveTypes::Boolean:
            return 'Z';
        case JPrimitiveTypes::Byte:
            return 'B';
        case JPrimitiveTypes::Char:
            return 'C';
        case JPrimitiveTypes::Short:
            return 'S';
        case JPrimitiveTypes::Integer:
            return 'I';
        case JPrimitiveTypes::Long:
            return 'J';
        case JPrimitiveTypes::Float:
            return 'F';
        case JPrimitiveTypes::Double:
            return 'D';
        case JPrimitiveTypes::Object:
            return 'L';
        case JPrimitiveTypes::Array:
            return '[';
        default:
            return 0;
    }
}


class JType {
protected:
    JPrimitiveTypes primitiveType;
public:
    JType(JPrimitiveTypes primitiveType) : primitiveType(primitiveType) {}

    virtual std::string compileSignature() const {
        std::string result;
        result += compilePrimitiveType(primitiveType);
        return result;
    }
};

class JObjectType : public JType {
protected:
    std::string classJNIIdentifier;
public:
    JObjectType(const std::string &classJniIdentifier) : JType(JPrimitiveTypes::Object),
                                                         classJNIIdentifier(classJniIdentifier) {}

    std::string compileSignature() const override {
        return std::string("L") + classJNIIdentifier + ";";
    }
};

class JArrayType : public JType {
protected:
    JType elementType;
public:
    JArrayType(JType elementType) : JType(JPrimitiveTypes::Array),
                                    elementType(elementType) {}

    std::string compileSignature() const override {
        return std::string("[") + elementType.compileSignature();
    }
};


std::string compileSignature(JType returnType, std::vector<JType> argTypes) {
    std::string result("(");
    for (const auto &argType: argTypes) {
        result += argType.compileSignature();
    }
    result += ")";
    result += returnType.compileSignature();
    return result;
}

std::string compileSignature(JType returnType, std::initializer_list<JType> elements) {
    return compileSignature(returnType, std::vector<JType>(elements.begin(), elements.end()));
}


class MarkerTaggedSpaceInterface {
    jobject jthis;
    JNIEnv *env;
public:
    MarkerTaggedSpaceInterface(JNIEnv *env, jobject jthis);


    bool getMarkerSpecs(
            int id,
            std::vector<cv::Vec3d> &outRvec,
            std::vector<cv::Vec3d> &outTvec,
            double &markerLength) const;


private:
    jclass getClass() const {
        return env->GetObjectClass(jthis);
    }

    jmethodID getMethodId(std::string methodName, std::string signature) const {
        return env->GetMethodID(getClass(), methodName.c_str(), signature.c_str());
    }

    template<typename signR, typename ...signArgTs>
    std::function<signR(signArgTs...)> javaMethodAsCallable(
            const std::string &signature,
            const std::string &methodName,
            const std::function<signR(JNIEnv *, jobject, jmethodID, jvalue *)> &jniMethodCaller
    ) const {
        const jmethodID mid = getMethodId(methodName, signature);

        if (mid == nullptr) {
            throw std::string("Cannot find method '") + methodName + "' with signature '"
                  + signature + "'";
        }

        return [&](signArgTs... args) {
            jvalue *data = {(jvalue *) args...};
            return jniMethodCaller(env, jthis, mid, data);
        };
    }


    jboolean test(jstring test){
        return javaMethodCall(Boolean, "test", { JObjectType("java/lang/String") },
                              <jboolean, jstring>)(test);
    }


};




#endif //ARUCOSLAM_MARKERTAGGEDSPACEINTERFACE_H
