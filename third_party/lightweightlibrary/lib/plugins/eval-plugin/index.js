window.evalPlugin = (evalParams) => {
    return new Function(`return(${evalParams.f})`)()
}